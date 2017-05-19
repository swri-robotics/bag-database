// *****************************************************************************
//
// Copyright (c) 2015, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

package com.github.swrirobotics.status;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.filesystem.BagScanner;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Service
public class StatusService implements StatusListener {
    @Autowired(required=false)
    private BagScanner myScanner;
    @Autowired
    private BagService myBagService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Map<String, Status> myStates = Maps.newHashMap();

    private final Queue<Status> myErrors = Queues.newArrayDeque();

    private final LatestStatus myLatestStatus = new LatestStatus();

    private final Logger myLogger = LoggerFactory.getLogger(StatusService.class);

    private static final int MAX_ERRORS = 10000;

    public class LatestStatus {
        private String source = "";
        private Status status = new Status(Status.State.IDLE, "");

        public void update(String source, Status status) {
            this.source = source;
            this.status = status;
        }

        public String getSource() {
            return source;
        }

        public Status getStatus() {
            return status;
        }

        public long getErrorCount() {
            return myErrors.size();
        }
    }

    @PostConstruct
    public void postConstruct() {
        if (myScanner != null) {
            myScanner.registerStatusListener(this);
        }
        myBagService.registerStatusListener(this);
    }

    @Override
    public void statusUpdate(String source, Status status) {
        myLogger.trace("statusUpdate: " + source + ": " + status.getTime().toString() +
                " / " + status.getState().toString() + " / " + status.getMessage());
        myStates.put(source, status);
        if (status.getState() == Status.State.ERROR) {
            if (myErrors.size() >= MAX_ERRORS) {
                myErrors.remove();
            }
            myErrors.add(status);
        }
        if (status.getTime().after(myLatestStatus.getStatus().getTime())) {
            myLatestStatus.update(source, status);
            messagingTemplate.convertAndSend("/topic/status", myLatestStatus);
        }
    }

    public LatestStatus getLatestStatus() {
        return myLatestStatus;
    }

    public Queue<Status> getErrors() {
        return myErrors;
    }

    public Map<String, Status> getStates() {
        return myStates;
    }

    public void clearErrors() {
        myErrors.clear();
        statusUpdate("UI", new Status(Status.State.IDLE, "Cleared errors."));
    }
}
