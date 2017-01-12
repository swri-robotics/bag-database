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

Ext.define('BagDatabase.views.BagDetailsWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.bagDetailsWindow',
    requires: ['BagDatabase.models.MessageType',
               'BagDatabase.models.Topic',
               'BagDatabase.views.BagPropertyGrid',
               'BagDatabase.views.TagGrid',
               'BagDatabase.views.MessageTypeGrid',
               'BagDatabase.views.TopicGrid'],
    layout: 'border',
    title: 'Bag Details',
    bagId: null,
    width: 1200,
    height: 650,
    loadmask: null,
    tagloadmask: null,
    constrainHeader: true,
    listeners: {
        show: function(win) {
            if (!win.loadmask) {
                win.loadmask = new Ext.LoadMask({
                        msg: 'Loading bag...',
                        target: win
                    });
            }
            win.loadmask.show();
        }
    },
    reloadTags: function() {
        var me = this;
        var tagGrid = me.down('#tags');
        if (!me.tagloadmask) {
            me.tagloadmask = new Ext.LoadMask({
                msg: 'Reloading tags...',
                target: tagGrid
            });
        }
        me.tagloadmask.show();
        Ext.Ajax.request({
            url: 'bags/getTagsForBag',
            method: 'GET',
            params: {
                bagId: me.bagId
            },
            callback: function(options, success, response) {
                me.tagloadmask.hide();
                var tags = Ext.util.JSON.decode(response.responseText);
                tagGrid.getStore().loadRawData(tags);
            }
        });
    },
    initComponent: function() {
        var me = this;

        var params = {
            bagId: this.bagId
        };
        params[csrfName] = csrfToken;

        this.items = [{
            xtype: 'bagPropertyGrid',
            region: 'west'
        }, {
            xtype: 'panel',
            layout: {
                type: 'vbox',
                align: 'stretch'
            },
            region: 'center',
            items: [{
                xtype: 'messageTypeGrid',
                itemId: 'messageGrid',
                flex: 1,
                data: []
            }, {
                xtype: 'topicGrid',
                itemId: 'topics',
                flex: 1,
                bagId: this.bagId,
                data: []
            }]
        }, {
            xtype: 'tagGrid',
            region: 'east',
            itemId: 'tags',
            split: true,
            flex: 1,
            bagId: this.bagId,
            data: []
        }
        ];

        this.callParent(arguments);

        Ext.Ajax.request({
            url: 'bags/get',
            params: params,
            callback: function(options, success, response) {
                if (me.loadmask) {
                    me.loadmask.hide();
                }
                if (response.status == 401) {
                    Ext.Msg.alert('Session Timeout', 'Your session has timed out.  Please reload.');
                    return;
                }
                if (!success) {
                    Ext.Msg.alert('Error', 'Error loading bag: ' + response.statusText);
                    console.log('Error retrieving bag.');
                    me.close();
                    return;
                }

                var bag = Ext.util.JSON.decode(response.responseText);
                me.setTitle('Bag Details (' + bag.filename + ')');
                bag.createdOn = new Date(bag.createdOn);
                bag.endTime = new Date(bag.endTime);
                bag.startTime = new Date(bag.startTime);
                bag.updatedOn = new Date(bag.updatedOn);
                delete bag.expanded;
                delete bag.leaf;
                delete bag.parentId;
                me.down('bagPropertyGrid').setSource(bag);

                var mts = {};
                bag.messageTypes.forEach(function(mt) {
                    mts[mt.md5sum] = mt;
                })
                bag.topics.forEach(function(topic) {
                    topic.type = mts[topic.type];
                });
                me.down('#messageGrid').getStore().loadRawData(bag.messageTypes);
                me.down('#topics').getStore().loadRawData(bag.topics);
                me.down('#tags').getStore().loadRawData(bag.tags);
            }
        });
    }
});