package com.song;


import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class MyDelayedTask implements Delayed {
    // 任务的执行时间
    private int executeTime = 0;//代表元素执行时间
    private String name;//元素名称

    /**
     *
     * @param delay 元素延迟多久执行
     * @param name 元素名称
     */
    public MyDelayedTask(int delay, String name){
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND,delay);
        this.executeTime = (int)(calendar.getTimeInMillis() /1000 );
        this.name = name;
    }

    /**
     * 元素在队列中的剩余时间
     * @param unit
     * @return
     * *
     */
    @Override
    public long getDelay(TimeUnit unit){
        Calendar calendar = Calendar.getInstance();
        return executeTime - (calendar.getTimeInMillis()/1000);
    }

    /**
     * 元素排序
     * @param o the object to be compared.
     * @return
     */
    @Override
    public int compareTo(Delayed o) {
        long val = this.getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        return val == 0 ? 0 : (val < 0 ? -1 : 1);
    }

    @Override
    public String toString() {
        return "MyDelayedTask{" +
                "executeTime=" + executeTime +
                ", name='" + name + '\'' +
                '}';
    }

    public static void main(String[] args) throws InterruptedException {
        DelayQueue<MyDelayedTask> queue = new DelayQueue<>();//创建延迟队列 延迟阻塞队列

        queue.add(new MyDelayedTask(10,"李四"));//十秒后执行
        queue.add(new MyDelayedTask(5,"张三"));//五秒后执行
        queue.add(new MyDelayedTask(15,"宋宋"));//十五秒后执行

        System.out.println(new Date()+"start consume");

        while (queue.size() !=0){
            //MyDelayedTask delayedTask = queue.poll();//从队列中拉取元素 poll非阻塞方法，拉取不到元素则返回null
            MyDelayedTask delayedTask = queue.take();//从队列中拉取元素 take阻塞方法，拉去不到元素则一直等待

            if (delayedTask != null){
                System.out.println(new Date()+"start task"+ delayedTask);
            }
            //每隔一秒消费一次
            try {
               Thread.sleep(1000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
        /**
         * 打印顺序是按任务的执行时间
         * Mon Aug 07 21:40:02 CST 2023start taskMyDelayedTask{executeTime=1691415602, name='张三'}
         * Mon Aug 07 21:40:07 CST 2023start taskMyDelayedTask{executeTime=1691415607, name='李四'}
         * Mon Aug 07 21:40:15 CST 2023start taskMyDelayedTask{executeTime=1691415615, name='宋宋'}
         */
    }

}