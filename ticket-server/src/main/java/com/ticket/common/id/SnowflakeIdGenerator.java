package com.ticket.common.id;

import java.util.function.LongSupplier;

public class SnowflakeIdGenerator {
    public static final long EPOCH = 1767225600000L;
    private final long workerId;
    private final LongSupplier timeSource;
    private long lastTimestamp=EPOCH-1;
    private long sequence=0;
    
    public SnowflakeIdGenerator(long workerId) {
        // 委托给下面的构造器:校验逻辑只写一份,两个构造器共用
        this(workerId, System::currentTimeMillis);
    }

    public SnowflakeIdGenerator(long workerId, LongSupplier timeSource) {
        if (workerId > 1023 || workerId < 0) {
            throw new IllegalArgumentException("机器码超出合法范围");
        }
        if (timeSource == null) {
            throw new IllegalArgumentException("时间源不能为空");
        }
        this.workerId = workerId;
        this.timeSource = timeSource;
    }
    public synchronized  long nextId()
    {
        long curTime=timeSource.getAsLong();
        if(curTime<EPOCH) throw new IllegalStateException("时间戳不合法");
        long offset=lastTimestamp-curTime;
        if(offset>5) throw new IllegalStateException("时钟回拨超出正常范围");
        else if(offset<=5&&offset>0)
        {
            curTime=lastTimestamp;
        }
        if(curTime==lastTimestamp)
        {
            sequence=(sequence+1)&4095;
            if(sequence==0)
            {
                curTime = waitNextMillis(lastTimestamp);
            }
        }
        else sequence=0;
        lastTimestamp=curTime;
        return ((curTime-EPOCH)<<22)|(workerId<<12)|sequence;
    }
    private long waitNextMillis(long lastTimestamp) {
    long t = timeSource.getAsLong();
    while (t <= lastTimestamp) {   // 注意是 <=:时间等于 lastTimestamp 也还没前进
        t = timeSource.getAsLong();
    }
    return t;
}
    public static long extractTimestamp(long id)
    {
        return (id >> 22) + EPOCH;
    }
}
