// *****************************************************************************
//
// Copyright (c) 2021, Hatchbed, L. L. C.
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

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.MessageHandler;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageCollection;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.StringType;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.github.swrirobotics.support.web.Configuration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BagServiceTest extends WebAppConfigurationAware {
    @Autowired
    BagService myBagService;
    @MockBean
    ConfigService myConfigService;

    private static final Logger myLogger = LoggerFactory.getLogger(BagServiceTest.class);

    @Test
    public void testGetMetadata() throws BagReaderException {
        // Set up a mock bag file to provide some test data to the service
        BagFile mockBagFile = mock(BagFile.class);
        myLogger.info("testGetMetadata");
        Configuration tmpConfig = new Configuration();
        // Set up one topic that has good data on it, and one topic that has bad data
        tmpConfig.setMetadataTopics(new String[]{"good", "bad"});
        when(myConfigService.getConfiguration()).thenReturn(tmpConfig);
        doAnswer((i) -> {
            // Create a standard ROS std_msgs/String message
            MessageType msg = new MessageType("MSG: std_msgs/String\nstring data", new MessageCollection());
            StringType str = msg.getField("data");
            boolean isGood = i.getArgument(0, String.class).equals("good");
            if (isGood) {
                str.setValue("name: John Doe\nemail: jdoe@example.com\n");
            }
            else {
                str.setValue("name: John Doe\nemail: jdoe@example.com\nbadvalue\n");
            }
            var handler = i.getArgument(1, MessageHandler.class);
            handler.process(msg, null);
            myLogger.info(i.getArgument(1).toString());
            return null;
        }).when(mockBagFile).forMessagesOnTopic(any(), any());

        var metadata = myBagService.getMetadata(mockBagFile);

        assertEquals(2, metadata.size());
        assertEquals("John Doe", metadata.get("name"));
        assertEquals("jdoe@example.com", metadata.get("email"));
    }

    @Test
    public void testDecodeBgra() {
        byte[] data = {1, 2, 3, 4};
        var image = myBagService.decodeImage(1, 1, "bgra8", BufferedImage.TYPE_INT_ARGB, data);

        assertEquals(1, image.getHeight());
        assertEquals(1, image.getWidth());

        Color color = new Color(image.getRGB(0, 0));
        assertEquals(3, color.getRed());
        assertEquals(2, color.getGreen());
        assertEquals(1, color.getBlue());
    }

    @Test
    public void testDecodeRgba() {
        byte[] data = {1, 2, 3, 4};
        var image = myBagService.decodeImage(1, 1, "rgba8", BufferedImage.TYPE_INT_ARGB, data);

        assertEquals(1, image.getHeight());
        assertEquals(1, image.getWidth());

        Color color = new Color(image.getRGB(0, 0));
        assertEquals(1, color.getRed());
        assertEquals(2, color.getGreen());
        assertEquals(3, color.getBlue());
    }
}
