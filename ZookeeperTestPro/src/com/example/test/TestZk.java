package com.example.test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class TestZk implements Watcher{
	
	 /** 定义原子变量 */  
    AtomicInteger seq = new AtomicInteger(1); 
    
	static final String ADDR="127.0.0.1:2181";
	static final int SESSION_TIMEOUT=3000;
	static final String ROOTPATH="/temp";
	static final String CHildNode="/temp/childnode";
	
	static ZooKeeper zk=null;
	
	CountDownLatch countDoun=new CountDownLatch(1);
	/**
	 * 创建zk连接
	 */
	private void createRlease(){
		try {
			zk=new ZooKeeper(ADDR, SESSION_TIMEOUT, this);
			System.out.println("创建zk连接");
			
			countDoun.await();
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 创建节点
	 *  String 	create(String path, byte[] data, List<ACL> acl, CreateMode createMode)
		void 	create(String path, byte[] data, List<ACL> acl, CreateMode createMode, AsyncCallback.StringCallback cb, Object ctx)
	 * @param path
	 * @param data
	 * Exception :NodeExists ,NoNode (父节点),NoChildrenForEphemerals (临时节点不能有子节点),
	 * The maximum allowable size of the data array is 1 MB 
	 */
	private void createPath(String path,String data){
		try {
			
			zk.create(path, data.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
						
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * public Stat exists()
	 *  Stat 	exists(String path, boolean watch)
		void 	exists(String path, boolean watch, AsyncCallback.StatCallback cb, Object ctx)
		Stat 	exists(String path, Watcher watcher)
		void 	exists(String path, Watcher watcher, AsyncCallback.StatCallback cb, Object ctx)
	 * Return the stat of the node of the given path. Return null if no such a node exists. 
	 * 会设置该节点的watch
	 */
	
	private boolean pathIsExists(String path,boolean watch){
		try {
			
			Stat stat = zk.exists(path, true);
			
			if(stat != null){
				if(stat.getEphemeralOwner() == 0){
					System.out.println("该节点是持久节点"+path);
				}else{
					System.out.println("该节点是临时节点"+path);
				}
				return true;
			}else{
				return false;
			}
			
		} catch (KeeperException e1) {
			e1.printStackTrace();
			return false;
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			return false;
		}
	}
	/**
	 * Delete the node with the given path. The call will succeed if such a node exists, 
	 * and the given version matches the node's version (if the given version is -1, it matches any node's versions). 
	 * Exception:NoNode ,BadVersion ,NotEmpty 
	 * 会触发设置在该节点和它父节点的watch
	 * @param arg0
	 */
	private void deletePath(String path,int version){
		try {
			zk.delete(path, version);
			System.out.println("删除节点"+path);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (KeeperException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Stat 	setData(String path, byte[] data, int version)
			Set the data for the node of the given path if such a node exists and the given version matches
		 the version of the node (if the given version is -1, it matches any node's versions).
		void 	setData(String path, byte[] data, int version, AsyncCallback.StatCallback cb, Object ctx)
	 */
	private void setData(String path,String data){
		try {
			zk.setData(path, data.getBytes(), -1);
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/**
	 *  	getData(String path, boolean watch, AsyncCallback.DataCallback cb, Object ctx)
			byte[] 	getData(String path, boolean watch, Stat stat)
			void 	getData(String path, Watcher watcher, AsyncCallback.DataCallback cb, Object ctx)
			byte[] 	getData(String path, Watcher watcher, Stat stat)
	 */
	
	private String getDta(String path,Stat stat){
		try {
			return new String(zk.getData(path, true, null));
			
		} catch (KeeperException e) {
			e.printStackTrace();
			return "";
		} catch (InterruptedException e) {
			e.printStackTrace();
			return "";
		}
	}
	
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
			createRlease();
		}
		
	}

}
