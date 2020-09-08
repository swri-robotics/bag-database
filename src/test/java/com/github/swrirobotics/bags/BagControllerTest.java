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

package com.github.swrirobotics.bags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.MessageType;
import com.github.swrirobotics.persistence.Tag;
import com.github.swrirobotics.persistence.Topic;
import com.github.swrirobotics.support.web.BagList;
import com.github.swrirobotics.support.web.ExtJsFilter;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.sql.Timestamp;
import java.util.ArrayList;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BagControllerTest extends WebAppConfigurationAware {
    @MockBean
    private BagService bagService;

    public Bag makeTestBag() {
        Bag bag = new Bag();

        bag.setVehicle("Test Vehicle");
        bag.setLocation("Test Location");
        bag.setDescription("Test Description");
        bag.setCompressed(false);
        bag.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        bag.setDuration(1.0);
        bag.setEndTime(new Timestamp(System.currentTimeMillis()));
        bag.setExpanded(false);
        bag.setFilename("test.bag");
        bag.setHasPath(true);
        bag.setId(1L);
        bag.setIndexed(false);
        bag.setLeaf(true);
        bag.setMessageCount(0L);
        bag.setMissing(false);
        bag.setPath("/");
        bag.setSize(1L);
        bag.setStartTime(new Timestamp(System.currentTimeMillis()));
        bag.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
        bag.setVersion("2.0");

        Tag testTag = new Tag();
        testTag.setBag(bag);
        testTag.setValue("Tag 1 Value");
        testTag.setTag("Tag 1 Key");
        bag.getTags().add(testTag);

        testTag = new Tag();
        testTag.setBag(bag);
        testTag.setValue("Tag 2 Value");
        testTag.setTag("Tag 2 Key");
        bag.getTags().add(testTag);

        MessageType type = new MessageType();
        type.setMd5sum("acffd30cd6b6de30f120938c17c593fb");
        type.setName("rosgraph_msgs/Log");
        bag.getMessageTypes().add(type);

        Topic testTopic = new Topic();
        testTopic.setBag(bag);
        testTopic.setConnectionCount(1L);
        testTopic.setMessageCount(50L);
        testTopic.setTopicName("/rosout");
        testTopic.setType(type);
        bag.getTopics().add(testTopic);

        return bag;
    }

    public BagList makeTestBagList() {
        BagList bags = new BagList();
        Bag testBag = makeTestBag();
        bags.setBags(new ArrayList<>(){{add(testBag);}});
        bags.setTotalCount(1);
        return bags;
    }

    @Test
    public void getBag() throws Exception {
        when(bagService.getBag(1L)).thenReturn(makeTestBag());
        mockMvc.perform(get("/bags/get").param("bagId", "1")).andExpect(status().isOk());
    }

    @Test
    public void getBagImage() throws Exception {
        when(bagService.getImage(1L, "/topic", 1)).thenReturn(new byte[]{});
        mockMvc.perform(get("/bags/image")
                .param("bagId", "1")
                .param("topic", "/topic")
                .param("index", "1")).andExpect(status().isOk());
    }

    @Test
    public void downloadBag() throws Exception {
        when(bagService.getBag(1L)).thenReturn(makeTestBag());
        mockMvc.perform(get("/bags/download").param("bagId", "1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=test.bag"))
            .andExpect(header().string("Content-Transfer-Encoding", "application/octet-stream"));
    }

    @Test
    public void updateBag() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mockMvc.perform(get("/bags/update").content(mapper.writeValueAsString(makeTestBagList())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void search() throws Exception {
        ExtJsFilter tsFilter = new ExtJsFilter();
        tsFilter.setProperty("startTime");
        tsFilter.setOperator("lt");
        tsFilter.setValue(Long.toString(System.currentTimeMillis()));
        ExtJsFilter[] filters = new ExtJsFilter[]{tsFilter};
        when(bagService.findBagsContainingText("Key",
            new String[]{"tags"},
            filters,
            1,
            100,
            "ASC",
            "filename"))
            .thenReturn(makeTestBagList());
        ObjectMapper mapper = new ObjectMapper();
        mockMvc.perform(get("/bags/search")
            .param("text", "Key")
            .param("fields", "tags")
            .param("page", "1")
            .param("limit", "100")
            .param("sort", "filename")
            .param("dir", "ASC")
            .param("filter", mapper.writeValueAsString(filters))
            .param("fillTopics", "true")
            .param("fillTypes", "true")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1L))
        .andExpect(jsonPath("$.bags[0].filename").value("test.bag"));
    }

    @Test
    public void getTagsForBag() throws Exception {
        when(bagService.getBag(1L)).thenReturn(makeTestBag());
        mockMvc.perform(get("/bags/getTagsForBag").param("bagId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tag").value("Tag 1 Key"))
            .andExpect(jsonPath("$[0].value").value("Tag 1 Value"));
    }

    @Test
    public void setTagsForBag() throws Exception {
        mockMvc.perform(get("/bags/setTag")
            .param("tagName", "Updated Key")
            .param("value", "Updated Value")
            .param("bagId", "1"))
            .andExpect(status().isOk());
    }

    @Test
    public void setTagForBags() throws Exception {
        mockMvc.perform(get("/bags/setTag")
            .param("tagName", "Updated Key")
            .param("value", "Updated Value")
            .param("bagIds", "1", "2", "3"))
            .andExpect(status().isOk());
    }

    @Test
    public void removeTagsForBag() throws Exception {
        mockMvc.perform(get("/bags/removeTags")
            .param("tagNames", "Tag 1", "Tag 2", "Tag 3")
            .param("bagId", "1"))
            .andExpect(status().isOk());
    }

    @Test
    public void getGpsCoordsForBag() throws Exception {
        when(bagService.getGpsCoordsForBags(new ArrayList<>(){{add(1L); add(2L); add(3L);}}))
            .thenReturn(new ArrayList<>() {{
                add(new Double[]{1.0, 2.0});
                add(new Double[]{1.0, 3.0});
                add(new Double[]{2.0, 3.0});
            }});
        mockMvc.perform(get("/bags/coords")
            .param("bagIds", "1", "2", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0][0]").value("1.0"))
            .andExpect(jsonPath("$[0][1]").value("2.0"))
            .andExpect(jsonPath("$[1][0]").value("1.0"))
            .andExpect(jsonPath("$[1][1]").value("3.0"))
            .andExpect(jsonPath("$[2][0]").value("2.0"))
            .andExpect(jsonPath("$[2][1]").value("3.0"))
        ;
    }
}
