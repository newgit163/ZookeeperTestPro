package com.example.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * 监听/lock节点删除事件的方式存在性能问题，其他连接不断请求服务器；同时维护的watch太多，
 * 而且可能出现KeeperErrorCode = NoNode异常、连接断掉没有删除lock节点，后续连接不能获取锁的问题
 * 
 * 而顺序临时节点，后一个节点监听前一个节点删除事件，同时触发的只有一个watch，而且不会出现异常事件
 *
 * EPHEMERAL lock
 * 前一个线程释放锁，后一个线程得到通知，获得锁，每个线程不会无完无了的一直请求zk服务器，且基本没有NoNode异常
 * 
 */
public class ZookeeperEphemeralLock implements Watcher {
	
	private static int THREAD_NUM=9;
	private static CountDownLatch countDoun = new CountDownLatch(1);
	private static CountDownLatch threadSemaphore=new CountDownLatch(THREAD_NUM);
	private int threadNum;
	private ZooKeeper zk=null;
	private String selfPath;
	private String waitPath;
	
	private static String ROO_TPATH="/temp";
	private static String SUB_PATH="/temp/lock";
	private static String CONNECTINT_LIST="127.0.0.1:2181";
	private static int SESSION_TIME_OUT=10000;       //默认session_time_out在2-20*tickTime
	
	public ZookeeperEphemeralLock(int id){
		this.threadNum=id;
	}
	//创建连接
	public void createConnection(){
		System.out.println("第"+threadNum+"个线程创建连接");
		try {
			zk=new ZooKeeper(CONNECTINT_LIST, SESSION_TIME_OUT, this);
			countDoun.await();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//清除root_path
	public static void distroy(){
		ZookeeperEphemeralLock quit = new ZookeeperEphemeralLock(0);
		 quit.createConnection();
		try {
			Stat stat =quit.zk.exists(ROO_TPATH, false);
			if(stat !=null){
				quit.zk.delete(ROO_TPATH, -1);
			}
		} catch (KeeperException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		System.out.println("清除root_path成功");
		quit.releaseConnect();
	}
	
	//清除连接
	public void releaseConnect(){
		if(this.zk != null){
			try {
				zk.close();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("释放连接");
		
	}
	
	//创建根节点，永久的
	public void createRootPath(){
		try {
			zk.create(ROO_TPATH, "root".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			
			System.out.println("----------------创建根节点root_path");
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//获取锁
	public void getLock(){
		//首先创建自己的顺序子节点
		try {
			selfPath=zk.create(SUB_PATH, "sub_path".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
			System.out.println("创建锁路径:"+selfPath);
			//判断是否是第一个子节点
			if(checkMiniZnode()){
				
				Thread.sleep(2000);
				
				getLockSuccess();
			}
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	//判断自己是否是第一个子节点
	public boolean checkMiniZnode(){
		try {
			List<String> znodeList = zk.getChildren(ROO_TPATH, false);
			if(znodeList.size() == 1) {    //只有一个节点，自己就是最小的，直接返回
				System.out.println("老子是唯一的");
				return true;
			}
			Collections.sort(znodeList);
			int index=znodeList.indexOf(selfPath.substring(ROO_TPATH.length()+1));
			
			switch (index) {
			case -1:
				System.out.println("该子节点 ----"+selfPath+" 已不存在");
				return false;
			case 0:
				System.out.println("我是第一个子节点");
				return true;
			default:
				//设置次节点为监控节点；这里也可以设置监控父节点的子节点变化情况，不同之处就是维护的监听器数量
				waitPath=ROO_TPATH+"/"+znodeList.get(index-1);
				try {
					zk.getData(waitPath, true, new Stat());    //这里监控此节点，有可能在此时这个此节点被删除了，出现NOnode异常；出现异常就判断次节点是否存在，再次检查子节点
				} catch (Exception e) {
					Stat stat = zk.exists(waitPath, false);
					if(stat == null){
						System.out.println("次节点已经失踪。我是老大了！");
						return checkMiniZnode();  //再次检查
					}
				}
				return false;
			}
		} catch (KeeperException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	//获取到锁，干活
	public void getLockSuccess(){
		//再次检查子节点
		try {
			if(zk.exists(selfPath, false) == null){
				System.out.println("子节点已经不存在了？");
				return;
			}
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("自己得到锁了-------------干活!");
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("活干完了-----------打扫战场");
		//删除子节点；这里我有个疑问就是是否需要显示的删除这个临时顺序节点
/*		try {
			zk.delete(selfPath, -1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (KeeperException e) {
			e.printStackTrace();
		}*/
		//释放连接
		releaseConnect();
		threadSemaphore.countDown();//销毁一个减少一个
	}

	@Override
	public void process(WatchedEvent event) {
		System.out.println(Thread.currentThread().getName()+"进入--------------process ----------处理");

		String path = event.getPath();
		EventType eventType = event.getType();
		KeeperState keeperState = event.getState();

		System.out.println("收到Watcher通知");
		System.out.println("连接状态:\t" + keeperState.toString());
		System.out.println("事件类型:\t" + eventType.toString());

		if (keeperState == KeeperState.SyncConnected) {
			if (eventType == EventType.None) {

				System.out.println("成功连接zk");
				countDoun.countDown();

			} else if (EventType.NodeDeleted == eventType && path.equals(waitPath)) {
				
				System.out.println("上头的家伙挂了,该我干活了" + path);
				
				if(checkMiniZnode()){
					getLockSuccess();
				}
			}
		} else if (KeeperState.AuthFailed == keeperState) {
			System.out.println("认证失败");
		} else if (KeeperState.Disconnected == keeperState) {
			System.out.println("连接断开");
		} else if (KeeperState.Expired == keeperState) {
			System.out.println("会话失效");
			//createConnection();
		}
	}
	

	public static void main(String[] args) {
		
		for(int i=1 ;i <= THREAD_NUM; i++){
			final int id=i;
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					
					try{  
						ZookeeperEphemeralLock dc = new ZookeeperEphemeralLock(id);  
                        dc.createConnection();  
                        //ROOT_PATH不存在的话，由一个线程创建即可； 
                        synchronized(threadSemaphore){
                        	
                        	if(dc.zk.exists(ROO_TPATH, false) ==  null){
                                dc.createRootPath();  //创建root节点  
                            }  
                        }
                        dc.getLock();  
                    } catch (Exception e){  
                        System.out.println("【第"+id+"个线程】 抛出的异常：");  
                        e.printStackTrace();  
                    }  
                }  
					
			}).start();
		}
		
		try {
			threadSemaphore.await();
			System.out.println("所有线程运行结束");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//清除根节点root
		distroy();
	}
}
