/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component.progress;

import com.evolveum.midpoint.schema.statistics.EnvironmentalPerformanceInformation;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingsStatisticsEntryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingsStatisticsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.EnvironmentalPerformanceInformationType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavol Mederly
 */
public class MappingsLineDto {

    public static final String F_OBJECT = "object";
    public static final String F_COUNT = "count";
    public static final String F_AVERAGE_TIME = "averageTime";
    public static final String F_MIN_TIME = "minTime";
    public static final String F_MAX_TIME = "maxTime";
    public static final String F_TOTAL_TIME = "totalTime";

    private String object;
    private int count;
    private Long minTime;
    private Long maxTime;
    private long totalTime;

//    public MappingsLineDto(String object) {
//        this.object = object;
//    }

    public MappingsLineDto(MappingsStatisticsEntryType entry) {
        object = entry.getObject();
        count = entry.getCount();
        minTime = entry.getMinTime();
        maxTime = entry.getMaxTime();
        totalTime = entry.getTotalTime();
    }

    public String getObject() {
        return object;
    }

    public int getCount() {
        return count;
    }

    public Long getAverageTime() {
        if (count > 0) {
            return totalTime / count;
        } else {
            return null;
        }
    }

    public Long getMinTime() {
        return minTime;
    }

    public Long getMaxTime() {
        return maxTime;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public static List<MappingsLineDto> extractFromOperationalInformation(EnvironmentalPerformanceInformation environmentalPerformanceInformation) {
        EnvironmentalPerformanceInformationType environmentalPerformanceInformationType = environmentalPerformanceInformation.getAggregatedValue();
        MappingsStatisticsType mappingsStatisticsType = environmentalPerformanceInformationType.getMappingsStatistics();
        return extractFromOperationalInformation(mappingsStatisticsType);
    }

    protected static List<MappingsLineDto> extractFromOperationalInformation(MappingsStatisticsType mappingsStatisticsType) {
        List<MappingsLineDto> retval = new ArrayList<>();
        if (mappingsStatisticsType == null) {
            return retval;
        }
        for (MappingsStatisticsEntryType entry : mappingsStatisticsType.getEntry()) {
            retval.add(new MappingsLineDto(entry));
        }
        return retval;
    }

//    private static MappingsLineDto findLineDto(List<MappingsLineDto> list, String object) {
//        for (MappingsLineDto lineDto : list) {
//            if (StringUtils.equals(lineDto.getObject(), object)) {
//                return lineDto;
//            }
//        }
//        return null;
//    }
//
//    private void setValue(int count, int min, int max, long totalDuration) {
//        this.count += count;
//        if (minTime == null || min < minTime) {
//            minTime = min;
//        }
//        if (maxTime == null || max > maxTime) {
//            maxTime = max;
//        }
//        totalTime += totalDuration;
//    }
}
