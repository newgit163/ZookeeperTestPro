package com.example.testZookeeper;

import java.io.IOException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

public class TestZookeeper {
	private static String HOST="127.0.0.1:2181";
	private static int  TIMEOUT=2000; //ms��λ
	
	
	public static void main(String[] args) {
		ZooKeeper zk=null;
		try {
			zk = new ZooKeeper(HOST, TIMEOUT, new Watcher() {
				 // ������б��������¼�
				@Override
				public void process(WatchedEvent arg0) {
			        System.out.println("�Ѿ�������" + arg0.getType() + "�¼���"); 
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			System.out.println(0xFFFF);
			zk.create("/testzk", "testzk".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		byte[] s;
		try {
			s = zk.getData("/testzk", null, null);
			System.out.println(new String(s));
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
		
		
	}
	

}
