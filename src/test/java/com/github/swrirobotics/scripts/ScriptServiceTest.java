// *****************************************************************************
//
// Copyright (c) 2020, Southwest Research Institute® (SwRI®)
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

package com.github.swrirobotics.scripts;

import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.github.swrirobotics.persistence.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScriptServiceTest extends WebAppConfigurationAware {
    @Autowired
    private ScriptService scriptService;
    @Autowired
    private BagRepository bagRepository;
    @Autowired
    private ScriptRepository scriptRepository;
    @Autowired
    private TopicRepository topicRepository;

    @Transactional
    public Long insertTestBag() {
        MessageType mt = new MessageType();
        mt.setName("test_msgs/Test");
        mt.setMd5sum("2d3a8cd499b9b4a0249fb98fd05cfa48");

        Bag bag = new Bag();
        bag.setVersion("2.0");
        bag.setSize(1L);
        bag.setIndexed(false);
        bag.setCompressed(false);
        bag.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        bag.setMissing(false);
        bag.setMd5sum("2d3a8cd499b9b4a0249fb98fd05cfa48");
        bag.setMessageCount(10L);
        bag.setFilename("test.bag");
        bag.setPath("/test");
        bag.getMessageTypes().add(mt);
        mt.getBags().add(bag);
        bagRepository.save(bag);

        Topic topic = new Topic();
        topic.setType(mt);
        topic.setTopicName("/topic");
        topic.setMessageCount(10L);
        topic.setConnectionCount(50L);
        bag.getTopics().add(topic);
        topic.setBag(bag);
        mt.getTopics().add(topic);
        topicRepository.save(topic);

        return bag.getId();
    }

    @Transactional
    public Long insertTestScript(String filename, String directory, String messageTypes, String topics) {
        Script script = new Script();
        script.setName("Test script");
        script.setRunAutomatically(true);
        script.setAllowNetworkAccess(false);
        script.setScript("");
        ScriptCriteria sc = new ScriptCriteria();
        sc.setScript(script);
        script.getCriteria().add(sc);
        sc.setTopicNames(topics);
        sc.setMessageTypes(messageTypes);
        sc.setFilename(filename);
        sc.setDirectory(directory);

        scriptRepository.save(script);

        return script.getId();
    }

    @Test
    public void testBagMatchesScriptCriteria() throws Exception {
        Long bagId = insertTestBag();
        Long fullMatchScriptId = insertTestScript("test.bag", "/test", "test_msgs/Test", "/topic");
        Long emptyScriptId = insertTestScript("", "", "", "");
        Long matchFileScriptId = insertTestScript(".*est\\.bag", "", "", "");
        Long matchDirectoryScriptId = insertTestScript("", "/t.*", "", "");
        Long matchMessageTypeScriptId = insertTestScript("", "", "test_msgs/Test", "");
        Long matchTopicScriptId = insertTestScript("", "", "", "/topic");
        Long failFileScriptId = insertTestScript("bad.bag", "", "", "");
        Long failDirectoryScriptId = insertTestScript("", "/bad", "", "");
        Long failMessageTypeScriptId = insertTestScript("", "", "test_msgs/Test, bad_msgs/Msg", "");
        Long failTopicsScriptId = insertTestScript("", "", "", "/topic, /topic2");
        assertTrue(scriptService.bagMatchesCriteria(bagId, fullMatchScriptId));
        assertTrue(scriptService.bagMatchesCriteria(bagId, emptyScriptId));
        assertTrue(scriptService.bagMatchesCriteria(bagId, matchFileScriptId));
        assertTrue(scriptService.bagMatchesCriteria(bagId, matchDirectoryScriptId));
        assertTrue(scriptService.bagMatchesCriteria(bagId, matchMessageTypeScriptId));
        assertTrue(scriptService.bagMatchesCriteria(bagId, matchTopicScriptId));
        assertFalse(scriptService.bagMatchesCriteria(bagId, failFileScriptId));
        assertFalse(scriptService.bagMatchesCriteria(bagId, failDirectoryScriptId));
        assertFalse(scriptService.bagMatchesCriteria(bagId, failMessageTypeScriptId));
        assertFalse(scriptService.bagMatchesCriteria(bagId, failTopicsScriptId));
    }
}
