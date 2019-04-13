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
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LeastActiveLoadBalance
 * <p>
 * Filter the number of invokers with the least number of active calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // 得到 invoker 的个数,invoker理解为服务的提供者也即是消费者消费服务时服务的执行者
        int length = invokers.size();
        // 最小的活跃数
        int leastActive = -1;
        // 具有相同活跃数的 invoker 的个数
        int leastCount = 0;
        // 用于记录具有相同“最小活跃数”的 Invoker 在 invokers 列表中的下标
        int[] leastIndexes = new int[length];
        // 每一个invoker的活跃数
        int[] weights = new int[length];
        // 总的活跃数
        int totalWeight = 0;
        // 第一个 invoker 的活跃数
        int firstWeight = 0;
        // 如果所有活跃数相同就是true
        boolean sameWeight = true;
        /**
         * 循环的作用
         * 1. 统计所有的活跃数之和及得到各自invoker的活跃数 totalWeight weights
         * 2. 得到是否所有的活跃数都相同 sameWeight
         * 3. 统计相同活跃数的记录到 leastIndexes
         */
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            int afterWarmup = getWeight(invoker, invocation);
            weights[i] = afterWarmup;
            if (leastActive == -1 || active < leastActive) {
                leastActive = active;
                leastCount = 1;
                leastIndexes[0] = i;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (active == leastActive) {
                leastIndexes[leastCount++] = i;
                totalWeight += afterWarmup;
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // 当只有一个 Invoker 具有最小活跃数，此时直接返回该 Invoker 即可，当然了,就你最闲就给你
        if (leastCount == 1) {
            return invokers.get(leastIndexes[0]);
        }
        // 活跃数有不相等的
        if (!sameWeight && totalWeight > 0) {
            // 随机生成一个 [0, totalWeight) 之间的数字
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // 循环让随机数减去具有最小活跃数的 Invoker 的活跃数，当 offset 小于等于0时，返回相应的 Invoker
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // 如果活跃数都相等就随机选择一个好了
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}