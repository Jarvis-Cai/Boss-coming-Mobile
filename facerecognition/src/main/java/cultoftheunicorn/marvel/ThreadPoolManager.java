/*
 * Copyright (c) 2019 SYKEAN Limited.
 *
 * All rights are reserved.
 * Proprietary and confidential.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Any use is subject to an appropriate license granted by SYKEAN Company.
 */

package cultoftheunicorn.marvel;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadPoolManager
 *
 * @author yx
 * @date 2019/5/17 13:57
 */
public class ThreadPoolManager {

    private final static String TAG = "ThreadPoolManager";

    private static final String THREAD_NAME_COMMON = "threadPool-common";

    private static final String THREAD_NAME_SCHEDULE = "threadPool-schedule";
    /**
     * 普通线程池
     */
    private ThreadPoolExecutor mPoolExecutor;

    /**
     * Scheduled线程池
     */
    private ScheduledThreadPoolExecutor mScheduledPoolExecutor;

    /**
     * 可以控制任务生命周期的任务队列
     */
    private Map<String, List<WeakReference<Future<?>>>> mTaskMap;

    /**
     * 单例
     */
    private static ThreadPoolManager instance = null;

    /**
     * 普通线程池核心线程池的数量，同时能够执行的线程数量
     */
    private int mCommonCorePoolSize = Runtime.getRuntime().availableProcessors() * 2 + 1;

    /**
     * 核心线程池的数量，同时能够执行的线程数量
     */
    private int mScheduleCorePoolSize = 2;
    /**
     * 最大线程池数量
     */
    private int mMaximumPoolSize = mCommonCorePoolSize + 10;

    /**
     * 存活时间,根据各种线程任务执行超时时间评估 （网络重连任务 超时）
     */
    private long mKeepAliveTime = 30;
    /**
     * TimeUnit
     */
    private TimeUnit unit = TimeUnit.SECONDS;

    private final int MAX_QUEUE_LENGTH = 255;

    /**
     * 创建一个新的实例 ThreadPoolManager
     */
    private ThreadPoolManager() {
        mPoolExecutor = new ThreadPoolExecutor(
                mCommonCorePoolSize,
                mMaximumPoolSize,
                mKeepAliveTime,
                unit,
                //缓冲队列，用于存放等待任务，Linked的先进先出
                new LinkedBlockingQueue<Runnable>(MAX_QUEUE_LENGTH),
                //创建线程的工厂
                new ThreadFactory() {
                    private final AtomicInteger mCount = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, THREAD_NAME_COMMON + "#" + mCount.getAndIncrement());
                    }
                }
        );
        // 用来对超出maximumPoolSize的任务的处理策略
        mPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                System.out.println("rejectedExecution happaned ");
                try {
                    super.rejectedExecution(r, e);
                } catch (RejectedExecutionException exception) {
                    exception.printStackTrace();
                }
            }
        });

        mScheduledPoolExecutor =
                new ScheduledThreadPoolExecutor(mScheduleCorePoolSize, new ThreadFactory() {
                    private final AtomicInteger mCount = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, THREAD_NAME_SCHEDULE + "#" + mCount.getAndIncrement());
                    }
                });
        mScheduledPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                System.out.println("rejectedExecution happaned ");
                try {
                    super.rejectedExecution(r, e);
                } catch (RejectedExecutionException exception) {
                    exception.printStackTrace();
                }
            }
        });
        mTaskMap = new WeakHashMap<String, List<WeakReference<Future<?>>>>();
    }

    /**
     * 获取ThreadPoolManager单例对象
     */
    public static ThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolManager.class) {
                if (instance == null) {
                    instance = new ThreadPoolManager();
                }
            }
        }
        return instance;
    }

    /**
     * 释放MinaThreadPoolManager中线程资源
     * 主要用于退出应用进程前，销毁线程池
     */
    public void release() {
        synchronized (ThreadPoolManager.class) {
            if (instance != null) {
                instance.cancelAllTaskThreads();
            }
            mPoolExecutor.shutdownNow();
            mScheduledPoolExecutor.shutdownNow();
            instance = null;
        }
    }

    /**
     * 重启线程
     * 如果线程任务不在线程池则等效于startTaskThread
     *
     * @param task 重启任务线程
     */
    public void restartTaskThread(Thread task) {
        if (task != null) {
            stopTaskThread(task);
            startTaskThread(task);
        }
    }

    /**
     * 重启线程
     *
     * @param task 任务
     * @param name 任务名称
     */
    public void restartTaskThread(FutureTask task, String name) {
        if (task != null && name != null) {
            stopTaskThread(name);
            startTaskThread(task, name);
        }
    }

    /**
     * 重启线程
     *
     * @param task 任务
     * @param name 任务名称
     */
    public void restartTaskThread(Runnable task, String name) {
        if (task != null && name != null) {
            stopTaskThread(name);
            startTaskThread(task, name);
        }
    }

    /**
     * 开启线程
     *
     * @param task 任务线程
     */
    public void startTaskThread(Thread task) {
        Future<?> request = mPoolExecutor.submit(task);
        String taskName = task.getName();
        addTask(request, taskName);
        System.out.println("startTaskThread add Thread name = " + taskName);
        printPoolExecutorInfo();
    }

    /**
     * 开启线程
     *
     * @param task 任务线程
     * @param name 任务名字
     */
    public void startTaskThread(FutureTask task, String name) {
        if (task != null && name != null) {
            Future<?> request = mPoolExecutor.submit(task);
            addTask(request, name);
            System.out.println("startTaskThread add FutureTask name = " + name);
            printPoolExecutorInfo();
        }
    }

    /**
     * 开启线程
     *
     * @param task 任务线程
     * @param name 任务名字
     */
    public void startTaskThread(Runnable task, String name) {
        if (task != null && name != null) {
            Future<?> request = mPoolExecutor.submit(task);
            addTask(request, name);
            System.out.println("startTaskThread add Runnable name = " + name);
            printPoolExecutorInfo();
        }
    }

    /**
     * 开启线程
     *
     * @param task 任务线程
     */
    public void executeTaskThread(Thread task) {
        String taskName = task.getName();
        System.out.println("executeTaskThread task name = " + taskName);
        printPoolExecutorInfo();
        mPoolExecutor.execute(task);
    }

    /**
     * 结束线程
     *
     * @param task 任务线程
     */
    public void stopTaskThread(Thread task) {
        stopTaskThread(task.getName());
    }

    /**
     * 结束线程
     *
     * @param taskTag 任务线程
     */
    public void stopTaskThread(String taskTag) {
        cancelTaskThreads(taskTag);
    }

    /**
     * 添加执行任务到队列中
     *
     * @param request
     */
    private void addTask(Future<?> request, String taskTag) {
        synchronized (ThreadPoolManager.class) {
            if (taskTag != null) {
                List<WeakReference<Future<?>>> requestList = mTaskMap.get(taskTag);
                if (requestList == null) {
                    requestList = new LinkedList<WeakReference<Future<?>>>();
                    mTaskMap.put(taskTag, requestList);
                }
                requestList.add(new WeakReference<Future<?>>(request));
            }
        }
    }

    /**
     * 取消所有的任务
     */
    public void cancelAllTaskThreads() {
        for (String clsName : mTaskMap.keySet()) {
            List<WeakReference<Future<?>>> requestList = mTaskMap.get(clsName);
            if (requestList != null) {
                Iterator<WeakReference<Future<?>>> iterator = requestList.iterator();
                while (iterator.hasNext()) {
                    Future<?> request = iterator.next().get();
                    if (request != null) {
                        request.cancel(true);
                    }
                }
            }
        }
        mTaskMap.clear();
    }

    /**
     * 根据特定任务名称取消任务
     */
    private void cancelTaskThreads(String taskName) {
        System.out.println("cancelTaskThreads task name = " + taskName);
        List<WeakReference<Future<?>>> requestList = mTaskMap.get(taskName);
        if (requestList != null) {
            Iterator<WeakReference<Future<?>>> iterator = requestList.iterator();
            while (iterator.hasNext()) {
                Future<?> request = iterator.next().get();
                if (request != null) {
                    request.cancel(true);
                }
            }
            mTaskMap.remove(taskName);
        }
        printPoolExecutorInfo();
    }

    public ThreadPoolExecutor getPoolExecutor() {
        return mPoolExecutor;
    }

    private void printPoolExecutorInfo() {
        if (mPoolExecutor != null) {
            System.out.println("mPoolExecutor info:[poolSize:" + mPoolExecutor.getPoolSize()
                    + "，activeCount:" + mPoolExecutor.getActiveCount()
                    + "，taskQueueCount:" + mPoolExecutor.getQueue().size()
                    + "，completeTaskCount：" + mPoolExecutor.getCompletedTaskCount() + "]");
        }
        if (mScheduledPoolExecutor != null) {
            System.out.println("mScheduledPoolExecutor info:[poolSize:" + mScheduledPoolExecutor.getPoolSize()
                    + "，activeCount:" + mScheduledPoolExecutor.getActiveCount()
                    + "，taskQueueCount:" + mScheduledPoolExecutor.getQueue().size()
                    + "，completeTaskCount：" +
                    mScheduledPoolExecutor.getCompletedTaskCount() + "]");
        }
    }

    /**
     * 执行在给定延迟后启用的一次性操作
     *
     * @param task  要执行的任务
     * @param delay 从现在开始延迟执行的时间
     * @param unit  延迟参数的时间单位
     */
    public void scheduleTaskThread(Thread task, long delay, TimeUnit unit) {
        Future<?> request = mScheduledPoolExecutor.schedule(task, delay, unit);
        addTask(request, task.getName());
    }

    /**
     * 执行在给定延迟后启用的一次性操作
     *
     * @param task     要执行的任务
     * @param taskName 任务名称
     * @param delay    从现在开始延迟执行的时间
     * @param unit     延迟参数的时间单位
     */
    public void scheduleTaskThread(Runnable task, String taskName, long delay,
                                   TimeUnit unit) {
        if (task != null && taskName != null) {
            Future<?> request = mScheduledPoolExecutor.schedule(task, delay, unit);
            addTask(request, taskName);
        }
    }

    /**
     * 周期性执行任务
     *
     * @param task         要执行的任务
     * @param initialDelay 从现在开始延迟执行的时间
     * @param period       执行间隔
     * @param unit         延迟参数的时间单位
     */
    public void scheduleAtFixedRate(Thread task, long initialDelay, long period, TimeUnit unit) {
        Future<?> request =
                mScheduledPoolExecutor.scheduleAtFixedRate(task, initialDelay, period, unit);
        addTask(request, task.getName());
    }

    /**
     * 周期性执行任务
     *
     * @param task         要执行的任务
     * @param taskName     任务名称
     * @param initialDelay 从现在开始延迟执行的时间
     * @param period       执行间隔
     * @param unit         延迟参数的时间单位
     */
    public void scheduleAtFixedRate(Runnable task, String taskName, long initialDelay,
                                    long period,
                                    TimeUnit unit) {
        if (task != null && taskName != null) {
            Future<?> request =
                    mScheduledPoolExecutor.scheduleAtFixedRate(task, initialDelay, period, unit);
            addTask(request, taskName);
        }
    }

    /**
     * 任务队列中是否有
     *
     * @param task 任务
     * @return 是否有标签任务 true-有
     */
    public boolean hasTask(Thread task) {
        if (task == null) {
            return false;
        }
        return hasTask(task.getName());
    }

    /**
     * 任务队列中是否有
     *
     * @param taskTag 任务标签
     * @return 是否有标签任务 true-有
     */
    public boolean hasTask(String taskTag) {
        if (taskTag == null) {
            return false;
        }
        List<WeakReference<Future<?>>> requestList = mTaskMap.get(taskTag);
        if (requestList != null) {
            Iterator<WeakReference<Future<?>>> iterator = requestList.iterator();
            while (iterator.hasNext()) {
                Future<?> request = iterator.next().get();
                if (request != null) {
                    if (request.isCancelled()) {
                        System.out.println(" taskTag: " + taskTag + " has canceled ");
                        continue;
                    }
                    if (!request.isDone()) {
                        System.out.println(" hasTask " + taskTag);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}