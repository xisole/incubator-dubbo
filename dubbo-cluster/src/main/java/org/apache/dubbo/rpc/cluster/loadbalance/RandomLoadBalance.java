/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * random load balance.
 */
public class RandomLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "random";
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // 执行者的列表,可抽象理解为服务的提供者列表(中间其实还需要网络通信一堆东西,暂时简单理解)
        int length = invokers.size();
        // 所有的执行者权重都一样的开关打开了
        boolean sameWeight = true;
        // 每个invoker 都有自己的权重,这里弄个数组出来
        int[] weights = new int[length];
        // 第一个权重
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // 所有的权重
        int totalWeight = firstWeight;
        // 这个循环是为了提取各个invoker 的权重 并且顺便看一下权重是否相同
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            weights[i] = weight;
            totalWeight += weight;
            // 检查权重和上一个权重是否相同,不同的话sameWeight置false,表示权重不是完全一样的设置
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        // 如果权重不相同并且有权重的话
        if (totalWeight > 0 && !sameWeight) {
            // 在这里取一个随机数
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < length; i++) {
                // 让随机值 offset 减去权重值，然后得到 invoker，这样可以比较容易得到 权重大的invoker
                offset -= weights[i];
                if (offset < 0) {
                    // 返回 invoker
                    return invokers.get(i);
                }
            }
        }
        // 所有权重都一样,那就随机选择一个就是了
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }
}
