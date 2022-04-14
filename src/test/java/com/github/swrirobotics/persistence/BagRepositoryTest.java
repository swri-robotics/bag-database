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

package com.github.swrirobotics.persistence;

import com.github.swrirobotics.config.WebAppConfigurationAware;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

import static org.junit.Assert.assertEquals;

@Rollback
public class BagRepositoryTest extends WebAppConfigurationAware {
    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private MessageTypeRepository mtRepository;

    @Autowired
    private TopicRepository topicRepository;

    private static final Logger myLogger = LoggerFactory.getLogger(BagRepositoryTest.class);

    @Transactional
    public Long insertBag() {
        Bag bag = new Bag();
        bag.setMd5sum("test");
        bag.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        bag.setDuration(0.0);
        bag.setStartTime(new Timestamp(System.currentTimeMillis()));
        bag.setEndTime(new Timestamp(System.currentTimeMillis()));
        bag.setFilename("test");
        bag.setPath("/test/");
        bag.setVersion("test");
        bag.setCompressed(false);
        bag.setIndexed(true);
        bag.setMessageCount(0L);
        bag.setMissing(true);
        bag.setSize(0L);
        bag = bagRepository.save(bag);

        Tag tag = new Tag();
        tag.setTag("Error");
        tag.setValue("False");
        tag.setBag(bag);
        bag.getTags().add(tag);

        MessageType mt = new MessageType();
        mt.setName("TestMessage");
        mt.setMd5sum("testmd5sum");
        mt.getBags().add(bag);
        mt = mtRepository.save(mt);
        bag.getMessageTypes().add(mt);

        Topic topic = new Topic();
        topic.setBag(bag);
        topic.setMessageCount(0L);
        topic.setType(mt);
        mt.getTopics().add(topic);
        topic.setConnectionCount(0L);
        topic.setTopicName("Test Topic");
        topic = topicRepository.save(topic);
        bag.getTopics().add(topic);

        bag = bagRepository.save(bag);

        return bag.getId();
    }

    @Test
    @Transactional
    public void addBag() {
        myLogger.info("Inserting bag.");
        Long bagId = insertBag();

        myLogger.info("Finding bag.");
        Bag bag = bagRepository.findById(bagId).orElseThrow();

        assertEquals(1, bag.getTopics().size());
        assertEquals(1, bag.getTags().size());
        assertEquals(1, bag.getMessageTypes().size());
    }
}
