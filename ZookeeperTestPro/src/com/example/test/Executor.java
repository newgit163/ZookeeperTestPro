package com.example.test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;


public class Executor implements Watcher, Runnable, DataMonitor.DataMonitorListener {
	String znode;
	DataMonitor dm;
	ZooKeeper zk;
	String filename;
	String exec[];
	Process child;

	public Executor(String hostPort, String znode, String filename, String[] exec) throws IOException {
		this.filename = filename;
		this.exec = exec;
		zk = new ZooKeeper(hostPort, 300, this);
		dm = new DataMonitor(zk, znode, null, this);
	}

	@Override
	public void run() {
		try {
			synchronized (this) {
				while (!dm.dead) {
					wait();
				}
			}
		} catch (Exception e) {
		}
	}

	@Override
	public void process(WatchedEvent event) {
		dm.process(event);
	}

	@Override
	public void exists(byte[] data) {
		if (data == null) {
			if (child != null) {
				System.err.println("killing process");
				child.destroy();
				try {
					child.waitFor();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			child = null;

		} else {
			if (child != null) {
				System.out.println("stopping child");
				child.destroy();
				try {
					child.waitFor();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			try {
				FileOutputStream fos = new FileOutputStream(filename);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				System.out.println("Starting child");
				child = Runtime.getRuntime().exec(exec);
				new StreamWriter(child.getInputStream(), System.out);
				new StreamWriter(child.getErrorStream(), System.err);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void closing(int rc) {
		synchronized (this) {
			notifyAll();
		}
	}

	static class StreamWriter extends Thread {
		OutputStream os;

		InputStream is;

		StreamWriter(InputStream is, OutputStream os) {
			this.is = is;
			this.os = os;
			start();
		}

		public void run() {
			byte b[] = new byte[80];
			int rc;
			try {
				while ((rc = is.read(b)) > 0) {
					os.write(b, 0, rc);
				}
			} catch (IOException e) {
			}

		}
	}
}
