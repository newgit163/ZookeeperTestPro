package com.example.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
/**
 * 监听/lock节点删除事件的方式存在性能问题，其他连接不断请求服务器；同时维护的watch太多，而且可能出现KeeperErrorCode = NoNode异常、连接断掉没有删除lock节点，后续连接不能获取锁的问题
 * 
 * 而顺序临时节点，后一个节点监听前一个节点删除事件，同时触发的只有一个watch，而且不会出现异常事件
 * @author leijin
 *
 */
public class DistributedZookeeper {
	
	private static CountDownLatch countDoun=new CountDownLatch(1);
	private ZooKeeper zk = null;
	private boolean isRentrant = false;

	public ZooKeeper getZk() {
		return zk;
	}

	public DistributedZookeeper(boolean isRentrant) {
		this.isRentrant = isRentrant;
		init();
	}

	private void init() {
		try {
			zk = new ZooKeeper("127.0.0.1:2181", 1000, new Watcher() {

				@Override
				public void process(WatchedEvent event) {
					System.out.println("进入--------------process ----------处理");
					
					try {
						Thread.currentThread().sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					String path=event.getPath();
					EventType eventType=event.getType();
					KeeperState keeperState=event.getState();
					
					System.out.println( "收到Watcher通知");  
			        System.out.println( "连接状态:\t" + keeperState.toString());  
			        System.out.println( "事件类型:\t" + eventType.toString());  
			        
					if(keeperState == KeeperState.SyncConnected){
						if(eventType == EventType.None){
							
							System.out.println("成功连接zk服务器，创建根节点");
							countDoun.countDown();
							
						}else if(EventType.NodeCreated == eventType){
							System.out.println("成功创建节点"+path);
							try {
								System.out.println("获取节点数据："+zk.getData(path, true, null));
							} catch (KeeperException e) {
								e.printStackTrace();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}else if(EventType.NodeDataChanged == eventType){
							System.out.println("节点数据变化"+path);
							try {
								System.out.println("获取节点数据："+zk.getData(path, true, null));
							} catch (KeeperException e) {
								e.printStackTrace();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}else if(EventType.NodeDeleted == eventType){
							System.out.println("该节点被删除"+path);
						}else if(EventType.NodeChildrenChanged == eventType){
							System.out.println("该节点子节点变化"+path);
							try {
								System.out.println("获取节点数据："+zk.getChildren(path, true));
							} catch (KeeperException e) {
								e.printStackTrace();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}else if(KeeperState.AuthFailed == keeperState){
						System.out.println("认证失败");
					}else if(KeeperState.Disconnected == keeperState){
						System.out.println("连接断开");
					}else if(KeeperState.Expired == keeperState){
						System.out.println("回话失效");
					}
				}
			});
			
			countDoun.await();
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// 创建根目录
		System.out.println("创建根目录");
		try {
			zk.create("/temp", "temp".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); 
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("创建根目录成功");
	}

	public void destroy() {
		// 删除根目录
		try {
			Stat stat = zk.exists("/temp", false);
			if(stat != null){
				zk.delete("/temp", -1);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (KeeperException e) {
			e.printStackTrace();
		}

		// 关闭zookeeper链接
		if (zk != null) {
			try {
				zk.close();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	public static void main(String[] args) {
		
		DistributedZookeeper dzk = new DistributedZookeeper(true);

		ArrayList<Thread> list = new ArrayList<Thread>();

		for (int i = 0; i < 4; i++) {
			
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					
					boolean locked = false;		//标记锁状态
					
					while (true) {

						System.out.println("线程" + Thread.currentThread().getName() + "开始尝试获取锁");
						try {
							dzk.getZk().create("/temp/lock", Thread.currentThread().getName().getBytes(),
									Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);    //这个最好创建临时性的，过期就删除
							System.out.println("线程" + Thread.currentThread().getName() + "获取锁成功");

							locked = true;

							System.out.println("线程" + Thread.currentThread().getName() + "开始处理业务逻辑...");

							Thread.currentThread().sleep(3000);

							System.out.println("线程" + Thread.currentThread().getName() + "业务逻辑处理完毕！");

						} catch (Exception e) {

							// 这里如果创建"/temp/lock"不成功，1、可能是其他线程创建了，获取了锁；2、是本线程自身持有这个lock，那么在异常捕获内验证获取的znode数据是否是本线程名
							
							if (dzk.isRentrant) {

								try {
									String znodeName = new String(dzk.getZk().getData("/temp/lock", true, null));

									// 当前线程与获取到的锁数据线程名一致，重入锁
									if (znodeName.equals(Thread.currentThread().getName())) {
										System.out.println("线程" + Thread.currentThread().getName() + "成功重入锁！");

										locked = true;

										System.out.println("线程" + Thread.currentThread().getName() + "开始处理业务逻辑...");

										Thread.currentThread().sleep(3000);

										System.out.println("线程" + Thread.currentThread().getName() + "业务逻辑处理完毕！");

									} else {
										System.out.println("线程" + Thread.currentThread().getName() + "尝试获取锁失败，锁被线程"
												+ znodeName + "占用！");
									}

								} catch (KeeperException e1) {   //在这里可能发送获取锁名的时候，被原来获取到锁的线程把锁释放了，KeeperErrorCode = NoNode for /temp/lock
																//也就是这种创建同一把锁方式的一个弊端	
									e1.printStackTrace();
								} catch (InterruptedException e1) {
									e1.printStackTrace();
								}

							} else {
								System.out.println("当前线程" + Thread.currentThread().getName() + "获取锁失败，非可重入锁，不需再试");
							}
						} finally {
							try {

								if (locked) {
									System.out.println("线程" + Thread.currentThread().getName() + "开始释放锁...");
									
									Thread.currentThread().sleep(3000);
									
									dzk.getZk().delete("/temp/lock", -1);
									System.out.println("线程" + Thread.currentThread().getName() + "成功释放锁！");
								}

							} catch (InterruptedException e) {   //这种方式情况下，在这里也会出问题，如果这个连接断掉了，那创建的持久节点没有删除，那么后续的连接不能获取到锁
								e.printStackTrace();
							} catch (KeeperException e) {
								e.printStackTrace();
							} finally {
								locked = false;    
							}
						}
					}

				}
			});

			list.add(thread);

			thread.start();
		}

		try {
			Thread.currentThread().sleep(1000 * 20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		for (int i = 0; i < list.size(); i++) {
			Thread thread = list.get(i);
			thread.stop();
		}

		// 释放资源
		dzk.destroy();

	}

}
