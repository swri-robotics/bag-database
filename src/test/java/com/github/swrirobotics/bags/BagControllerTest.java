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
import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.storage.BagStorage;
import com.github.swrirobotics.bags.storage.BagWrapper;
import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.MessageType;
import com.github.swrirobotics.persistence.Tag;
import com.github.swrirobotics.persistence.Topic;
import com.github.swrirobotics.support.web.BagList;
import com.github.swrirobotics.support.web.ExtJsFilter;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.payload.FieldDescriptor;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BagControllerTest extends WebAppConfigurationAware {
    @MockBean
    private BagService bagService;

    public BagWrapper makeTestBagWrapper() {
        return new BagWrapper() {
            @Override
            public void close() throws IOException {

            }

            @Override
            public BagFile getBagFile() throws BagReaderException {
                return new BagFile("/test.bag");
            }

            @Override
            public BagStorage getBagStorage() {
                return null;
            }

            @Override
            public String getPath() {
                return "/";
            }

            @Override
            public String getFilename() {
                return "test.bag";
            }

            @Override
            public Long getSize() throws IOException {
                return 1000L;
            }

            @Override
            public Resource getResource() throws FileNotFoundException {
                return new AbstractResource() {
                    @Override
                    public String getDescription() {
                        return "Test Resource";
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(new byte[1000]);
                    }
                };
            }
        };
    }

    public Bag makeTestBag() {
        Bag bag = new Bag();

        bag.setVehicle("Test Vehicle");
        bag.setStorageId("default");
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
        bag.setMessageCount(50L);
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

    public FieldDescriptor[] getBagListFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("Database ID of the bag file"),
            fieldWithPath("filename").description("Name of the bag file on disk"),
            fieldWithPath("path").description("Path to the bag file on disk"),
            fieldWithPath("version").description("ROS bag file version"),
            fieldWithPath("duration").description("Duration of the bag file in seconds"),
            fieldWithPath("startTime").description("Start time of the bag file"),
            fieldWithPath("endTime").description("End time of the bag file"),
            fieldWithPath("size").description("Size of the bag file in bytes"),
            fieldWithPath("messageCount").description("Number of messages in the bag file"),
            fieldWithPath("indexed").description("True if the bag file is indexed"),
            fieldWithPath("compressed").description("True if the bag file is compressed"),
            fieldWithPath("messageTypes").description("Message types in the bag file"),
            fieldWithPath("topics").description("Topics in the bag file"),
            fieldWithPath("createdOn").description("When the bag file was created"),
            fieldWithPath("missing").description("True if the bag file is missing on disk"),
            fieldWithPath("hasPath").description("True if we detected any GPS coordinates in the bag file"),
            fieldWithPath("vehicle").description("The name of the vehicle that recorded the bag file"),
            fieldWithPath("description").description("Friendly description of the bag file"),
            fieldWithPath("md5sum").description("The Bag Database's MD5 Sum of the bag contents; NOT the same as the file's MD5 sum").type("String"),
            fieldWithPath("location").description("Friendly location name for the bag file"),
            fieldWithPath("tags").description("Tags placed on the bag file"),
            fieldWithPath("updatedOn").description("The last time the bag file's database entry was modified"),
            fieldWithPath("parentId").description("For internal use only; used for organization in the Folder View").type("Number"),
            fieldWithPath("expanded").description("For internal use only; used for organization in the Folder View"),
            fieldWithPath("leaf").description("For internal use only; used for organization in the Folder View"),
            fieldWithPath("latitudeDeg").description("First latitude coordinate detected in the bag file").type("Number"),
            fieldWithPath("longitudeDeg").description("First longitude coordinate detected in the bag file").type("Number"),
            fieldWithPath("storageId").description("ID of the storage backend that hosts the bag file").type("String")
        };
    }

    public FieldDescriptor[] getMessageTypesFields() {
        return new FieldDescriptor[] {
            fieldWithPath("md5sum").description("ROS MD5 Sum of the message type"),
            fieldWithPath("name").description("Name of the ROS message type")
        };
    }

    public FieldDescriptor[] getTopicsFields() {
        return new FieldDescriptor[] {
            fieldWithPath("topicName").description("Name of the ROS topic"),
            fieldWithPath("bagId").description("Database ID of the bag file"),
            fieldWithPath("bag").description("Database ID of the bag file (redundant, for internal use only)"),
            fieldWithPath("messageCount").description("Number of messages on this topic"),
            fieldWithPath("type").description("MD5 Sum of this topic's message type"),
            fieldWithPath("connectionCount").description("Number of connections made on this topic")
        };
    }

    public FieldDescriptor[] getTagsFields() {
        return new FieldDescriptor[] {
            fieldWithPath("tag").description("Name of the tag"),
            fieldWithPath("bagId").description("Database ID of the bag file"),
            fieldWithPath("value").description("Value of the tag")
        };
    }

    public FieldDescriptor[] getGpsCoordinateFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[0]").description("Longitude coordinate"),
            fieldWithPath("[1]").description("Latitude coordinate")
        };
    }

    @Test
    public void getBag() throws Exception {
        when(bagService.getBag(1L)).thenReturn(makeTestBag());
        mockMvc.perform(get("/bags/get")
            .param("bagId", "1"))
            .andExpect(status().isOk())
        .andDo(document("bags/{method-name}",
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            requestParameters(
                parameterWithName("bagId").description("Database ID of the bag to download")
            ),
            responseFields(
                getBagListFields()
            )
            .andWithPrefix("messageTypes[].", getMessageTypesFields())
            .andWithPrefix("topics[].", getTopicsFields())
            .andWithPrefix("tags[].", getTagsFields())
        ));
    }

    @Test
    public void getBagImage() throws Exception {
        when(bagService.getImage(1L, "/topic", 1)).thenReturn(new byte[]{});
        mockMvc.perform(get("/bags/image")
                .param("bagId", "1")
                .param("topic", "/topic")
                .param("index", "1")).andExpect(status().isOk())
        .andDo(document("bags/{method-name}",
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            requestParameters(
                parameterWithName("bagId").description("Database ID of the bag to retrieve an image from"),
                parameterWithName("topic").description("Name of the image topic"),
                parameterWithName("index").description("Index of the message to retrieve on the message topic; note " +
                    "that this method must iterate through (N-1) messages to retrieve the Nth message on a topic, so" +
                    "this can be slow")
            )));
    }

    @Test
    public void downloadBag() throws Exception {
        when(bagService.getBagWrapper(1L)).thenReturn(makeTestBagWrapper());
        mockMvc.perform(get("/bags/download").param("bagId", "1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=test.bag"))
            .andExpect(header().string("Content-Type", "application/octet-stream"))
            .andExpect(header().string("Content-Length", "1000"))
        .andDo(document("bags/{method-name}",
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            requestParameters(
                parameterWithName("bagId").description("The database ID of the bag file to download")
            )));
    }

    @Test
    public void getBagStorageIds() throws Exception {
        when(bagService.getBagStorageIds()).thenReturn(Lists.newArrayList("default"));
        mockMvc.perform(get("/bags/get_storage_ids"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.storageIds").isArray())
            .andDo(document("bags/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("totalCount").description("Total number of storage backends"),
                    fieldWithPath("storageIds").description("A list of valid storage backend identifiers")
                )
                    .andWithPrefix("storageIds[].",
                        fieldWithPath("storageId").description("Unique identifier of storage backend"))
                ));
    }

    @Test
    public void updateBag() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mockMvc.perform(post("/bags/update")
            .with(csrf())
            .content(mapper.writeValueAsString(makeTestBagList())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andDo(document("bags/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestBody(
                    new HashMap<>() {{
                        put("totalCount", "The number of bags to be updated");
                        put("bags", "Bag data to update in the database");
                    }}
                ),
                responseFields(
                    fieldWithPath("success").description("True if the update was successful")
                )));
    }

    @Test
    public void uploadBag() throws Exception {
        MockMultipartFile bagFile = new MockMultipartFile("file",
            "bagfile.bag",
            "application/octet-stream",
            "<< binary bag data >>".getBytes());

        mockMvc.perform(multipart("/bags/upload")
                .file(bagFile)
                .with(csrf())
                .param("targetDirectory", "/")
                .param("storageId", "default"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value(""))
            .andDo(document("bags/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestParts(
                    partWithName("file").description("The bag file to upload")
                ),
                requestParameters(
                    parameterWithName("targetDirectory").description("Location to place the bag file on disk"),
                    parameterWithName("storageId").description("ID of the storage backend that should store the bag file; default: \"default\"").optional(),
                    parameterWithName("_csrf").description("CSRF token supplied by the Bag Database")
                )
                ));
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
        .andExpect(jsonPath("$.bags[0].filename").value("test.bag"))
        .andDo(document("bags/{method-name}",
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            requestParameters(
                parameterWithName("text").description("Text to search for in selected fields; will match if any of " +
                    "the selected fields include this string anywhere in their contents.  leave empty to not do a " +
                    "text search"),
                parameterWithName("fields").description("List of fields to search for text.  Note that searching " +
                    "both messageType and topicName at the same time is complex and may be slow.  May include any of: " +
                    "filename, description, tags, path, location, vehicle, messageType, topicName, tags").optional(),
                parameterWithName("page").description("Page number of results to return; 1 is the first page"),
                parameterWithName("limit").description("Maximum number of results per page"),
                parameterWithName("sort").description("Column to sort the results on; must be one of: " +
                    "id, path, filename, location, vehicle, description, latitudeDeg, longitudeDeg, missing, " +
                    "md5sum, duration, createdOn, updatedOn, startTime, endTime, size, messageCount"),
                parameterWithName("dir").description("Direction to sort results; must be either ASC or DESC"),
                parameterWithName("filter").description("A list of ExtJsFilter objects for columns that support " +
                    "filtering; may be empty to not perform filtering").optional(),
                parameterWithName("fillTopics").description("True to fill in the topic list for each bag, false to " +
                    "leave it empty").optional(),
                parameterWithName("fillTypes").description("True to fill in the list of message types for each bag, " +
                    "false to leave it empty").optional()
            ), responseFields(
                fieldWithPath("totalCount").description("Total number of bag files returned by the search"),
                fieldWithPath("bags").description("The requested page of bags that match the search")
            )
                .andWithPrefix("bags[].", getBagListFields())
                .andWithPrefix("bags[].messageTypes[].", getMessageTypesFields())
                .andWithPrefix("bags[].topics[].", getTopicsFields())
                .andWithPrefix("bags[].tags[].", getTagsFields())
        ));
    }

    @Test
    public void getTagsForBag() throws Exception {
        when(bagService.getBag(1L)).thenReturn(makeTestBag());
        mockMvc.perform(get("/bags/getTagsForBag").param("bagId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tag").value("Tag 1 Key"))
            .andExpect(jsonPath("$[0].value").value("Tag 1 Value"))
            .andExpect(jsonPath("$[1].tag").value("Tag 2 Key"))
            .andExpect(jsonPath("$[1].value").value("Tag 2 Value"))
        .andDo(document("bags/{method-name}",
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            requestParameters(
                parameterWithName("bagId").description("Database ID of the bag to get tags for")
            ),
            responseFields().andWithPrefix("[].", getTagsFields())
        ));
    }

    @Test
    public void setTagForBag() throws Exception {
        mockMvc.perform(post("/bags/setTag")
            .param("tagName", "Updated Key")
            .param("value", "Updated Value")
            .param("bagId", "1")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(mvcResult -> { assertTrue(mvcResult.getResponse().getContentAsString().isEmpty()); })
            .andDo(document("bags/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestParameters(
                    parameterWithName("tagName").description("Tag name"),
                    parameterWithName("value").description("Tag value"),
                    parameterWithName("bagId").description("Database ID of the bag to tag"),
                    parameterWithName("_csrf").description("CSRF token supplied by the Bag Database")
                )));
    }

    @Test
    public void setTagForBags() throws Exception {
        mockMvc.perform(post("/bags/setTagForBags")
            .param("tagName", "Updated Key")
            .param("value", "Updated Value")
            .param("bagIds", "1", "2", "3")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(mvcResult -> { assertTrue(mvcResult.getResponse().getContentAsString().isEmpty()); })
            .andDo(document("bags/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestParameters(
                    parameterWithName("tagName").description("Tag name"),
                    parameterWithName("value").description("Tag value"),
                    parameterWithName("bagIds").description("Database IDs of the bags to tag"),
                    parameterWithName("_csrf").description("CSRF token supplied by the Bag Database")
                )));
    }

    @Test
    public void removeTagsForBag() throws Exception {
        mockMvc.perform(post("/bags/removeTags")
            .param("tagNames", "Tag 1", "Tag 2", "Tag 3")
            .param("bagId", "1")
            .with(csrf()))
            .andExpect(status().isOk())
            .andDo(document("bags/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestParameters(
                    parameterWithName("tagNames").description("Tag names to remove"),
                    parameterWithName("bagId").description("Database ID of the bag to remove tags from"),
                    parameterWithName("_csrf").description("CSRF token supplied by the Bag Database")
                )));
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
        .andDo(document("bags/{method-name}",
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            requestParameters(
                parameterWithName("bagIds").description("Database IDs of bags to get GPS coordinates for")
            ),
            responseFields(
                fieldWithPath("[]").description("A list of (longitude, latitude) coordinates from each bag file.")
            ).andWithPrefix("[].", getGpsCoordinateFields())
        ));
    }
}
