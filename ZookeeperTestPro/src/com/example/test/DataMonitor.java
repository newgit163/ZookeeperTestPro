package com.example.test;

import java.util.Arrays;

import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class DataMonitor implements Watcher,StatCallback {
	ZooKeeper zk;
	String znode;
	Watcher chainedWatcher;
	boolean dead;
	DataMonitorListener listener;
	byte prevDate[];
	public DataMonitor(ZooKeeper zk, String znode, Watcher chainedWatcher, DataMonitorListener listener) {
		this.zk = zk;
		this.znode = znode;
		this.chainedWatcher = chainedWatcher;
		this.listener = listener;
		zk.exists(znode, true, this, null);
	}
	
	public interface DataMonitorListener{
		  /**
         * The existence status of the node has changed.
         */
        void exists(byte data[]);

        /**
         * The ZooKeeper session is no longer valid.
         *
         * @param rc
         *                the ZooKeeper reason code
         */
        void closing(int rc);
	}

	@Override
	public void processResult(int rc, String path, Object ctx, Stat stat) {
		boolean exists = false;
		switch (rc) {
		case Code.Ok:
			exists=true;
			break;
		case Code.NoNode:
			exists=false;
			break;
		case Code.SessionExpired:
		case Code.NoAuth:
			dead=true;
			listener.closing(rc);
			return;
		default:
			zk.exists(znode, true, this, null);
			return;
		}
		
		byte b[]=null;
		if(exists){
			 try {
				b = zk.getData(znode, false, null);
			} catch (KeeperException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				return;
			}
		}
		
		if((b == null && b != prevDate) || (b != null && !Arrays.equals(prevDate, b))){
			listener.exists(b);
			prevDate = b;
		}
	}

	@Override
	public void process(WatchedEvent event) {
		String path=event.getPath();
		if(event.getType() == Event.EventType.None){
			switch (event.getState()) {
			case SyncConnected:
				
				break;
			case Expired:
				dead=true;
				listener.closing(KeeperException.Code.SessionExpired);
				break;
			}
		}else{
			if(path !=null && path.equals(znode)){
				zk.exists(znode, true, this, null);
			}
		}
		if(chainedWatcher != null){
			chainedWatcher.process(event);
		}
	}
	
	
}
