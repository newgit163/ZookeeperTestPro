package com.example.curatorApi;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.listen.ListenerEntry;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * @描述：第三种监听器的添加方式: Cache 的三种实现 实践 
 *  Path Cache：监视一个路径下1）孩子结点的创建、2）删除，3）以及结点数据的更新。产生的事件会传递给注册的PathChildrenCacheListener。
 *  Node Cache：监视一个结点的创建、更新、删除，并将结点的数据缓存在本地。
 *  Tree Cache：Path Cache和Node Cache的“合体”，监视路径下的创建、更新、删除事件，并缓存路径下所有孩子结点的数据。
 *
 */
public class CuratorWatch1 {
	private static final String CONNECT_ADDR = "127.0.0.1:2181";
	private static final int SESSION_TIMEOUT = 5000;

	public static void main(String[] args) throws Exception {
		RetryPolicy policy = new ExponentialBackoffRetry(1000, 10);
		CuratorFramework curator = CuratorFrameworkFactory.builder().connectString(CONNECT_ADDR)
				.sessionTimeoutMs(SESSION_TIMEOUT).retryPolicy(policy).build();
		curator.start();
		
		//1、三种cache
		PathChildrenCache pathCache = new PathChildrenCache(curator, "/super", false);
		TreeCache treeCache = new TreeCache(curator, "/super");
		NodeCache nodeCache = new NodeCache(curator, "/super", false);
		
		// 最后一个参数表示是否进行压缩
		nodeCache.start(true);

		// 2、三种监听器；只会监听节点的创建和修改，删除不会监听
		PathChildrenCacheListener pathListener = new PathChildrenCacheListener() {
			@Override
			public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
				System.out.println("pathCache路径：" + nodeCache.getCurrentData().getPath());
				System.out.println("pathCache数据：" + new String(nodeCache.getCurrentData().getData()));
				System.out.println("pathCache状态：" + nodeCache.getCurrentData().getStat());
			}
		};
		
		pathCache.getListenable().addListener(pathListener);

		nodeCache.getListenable().addListener(new NodeCacheListener(){
			@Override
			public void nodeChanged() throws Exception {
				System.out.println("nodeCache路径：" + nodeCache.getCurrentData().getPath());
				System.out.println("nodeCache数据：" + new String(nodeCache.getCurrentData().getData()));
				System.out.println("nodeCache状态：" + nodeCache.getCurrentData().getStat());
			}
		});
		
		treeCache.getListenable().addListener(new TreeCacheListener() {
			
			@Override
			public void childEvent(CuratorFramework client, TreeCacheEvent event) throws Exception {
				System.out.println("treeCache路径：" + nodeCache.getCurrentData().getPath());
				System.out.println("treeCache数据：" + new String(nodeCache.getCurrentData().getData()));
				System.out.println("treeCache状态：" + nodeCache.getCurrentData().getStat());
			}
		});
		
		treeCache.start();
		
		curator.create().forPath("/super", "1234".getBytes());
		System.out.println("----------------------------");

		Thread.sleep(1000);
		curator.setData().forPath("/super", "5678".getBytes());
		System.out.println("----------------------------");

		Thread.sleep(1000);
		curator.delete().forPath("/super");

		System.out.println("----------------------------");
		Thread.sleep(5000);

		curator.close();
	}
}
