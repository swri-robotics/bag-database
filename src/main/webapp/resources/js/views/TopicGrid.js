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

Ext.define('BagDatabase.views.TopicGrid', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.topicGrid',
    title: 'Topics',
    bagId: 0,
    store: {
        xtype: 'jsonstore',
        sorters: 'topicName',
        model: 'BagDatabase.models.Topic',
        reader: {
            type: 'json'
        }
    },
    columns: [{
        text: 'Topic Name', dataIndex: 'topicName', flex: 2
    }, {
        text: 'Message Type: ', dataIndex: 'messageType', flex: 2
    }, {
        text: 'Message MD5 Sum', dataIndex: 'md5sum', flex: 1, hidden: true
    }, {
        text: 'Messages', dataIndex: 'messageCount', flex: 1
    }, {
        text: 'Connections', dataIndex: 'connectionCount', flex: 1
    }, {
        xtype: 'actioncolumn',
        width: 25,
        items: [{
            iconCls: 'bag-action-icon images-icon',
            isDisabled: function(view, rowIndex, colIndex, item, record) {
                var mt = record.get('messageType');
                return mt !== 'sensor_msgs/Image' &&
                       mt !== 'sensor_msgs/CompressedImage';
            },
            getTip: function(value, metadata, record) {
                var mt = record.get('messageType');
                var isImage = (mt === 'sensor_msgs/Image' ||
                               mt === 'sensor_msgs/CompressedImage');
                return isImage ? 'Display First Image' : 'Not an image topic';
            },
            handler: function(grid, rowIndex, colIndex) {
                var record = grid.getStore().getAt(rowIndex);
                grid.ownerCt.showImage(record.get('topicName'));
            }
        }]
    }, {
        xtype: 'actioncolumn',
        width: 25,
        items: [{
            iconCls: 'bag-action-icon film-icon',
            isDisabled: function(view, rowIndex, colIndex, item, record) {
                var mt = record.get('messageType');
                return mt !== 'sensor_msgs/Image' &&
                       mt !== 'sensor_msgs/CompressedImage' &&
                       mt !== 'stereo_msgs/DisparityImage';
            },
            getTip: function(value, metadata, record) {
                var mt = record.get('messageType');
                var isImage = (mt === 'sensor_msgs/Image' ||
                               mt === 'sensor_msgs/CompressedImage' ||
                               mt === 'stereo_msgs/DisparityImage');
                return isImage ? 'Display Video' : 'Not an image topic';
            },
            handler: function(grid, rowIndex, colIndex) {
                var record = grid.getStore().getAt(rowIndex);
                grid.ownerCt.showVideo(record.get('topicName'), 1);
            }
        }]
    }, {
        xtype: 'actioncolumn',
        width: 25,
        items: [{
            iconCls: 'bag-action-icon film-go-icon',
            isDisabled: function(view, rowIndex, colIndex, item, record) {
                var mt = record.get('messageType');
                return mt !== 'sensor_msgs/Image' &&
                       mt !== 'sensor_msgs/CompressedImage' &&
                       mt !== 'stereo_msgs/DisparityImage';
            },
            getTip: function(value, metadata, record) {
                var mt = record.get('messageType');
                var isImage = (mt === 'sensor_msgs/Image' ||
                               mt === 'sensor_msgs/CompressedImage' ||
                               mt === 'stereo_msgs/DisparityImage');
                return isImage ? 'Display Video w/ Frame Skip' : 'Not an image topic';
            },
            handler: function(grid, rowIndex, colIndex) {
                Ext.Msg.prompt('Display Video with Frame Skip',
                    'Only display every nth frame:',
                    function(btnText, input) {
                        if (btnText === 'ok') {
                            var record = grid.getStore().getAt(rowIndex);
                            grid.ownerCt.showVideo(record.get('topicName'), input);
                        }
                    }, window, false, '10');
            }
        }]
    }],
    showImage: function(topic) {
        var win = Ext.create('Ext.window.Window', {
            title: topic,
            width: 720,
            height: 480,
            html: '<div></div>',
            listeners: {
                afterRender: function(win) {
                    var loadMask = Ext.create('Ext.LoadMask', {
                        msg: 'Loading...',
                        target: win
                    });
                    loadMask.show();
                    Ext.Ajax.request({
                        url: 'bags/image?bagId=' + this.bagId + "&topic=" + topic + '&index=0',
                        success: function(response) {
                            loadMask.hide();
                            win.setHtml(response.responseText);
                        },
                        failure: function(response) {
                            loadMask.hide();
                            win.setHtml(response.responseText);
                        }
                    });
                }.bind(this)
            }
        });
        win.show();
    },
    showVideo: function(topic, frameSkip) {
        var vidWidth = 720;
        var vidHeight = 480;
        var win = Ext.create('Ext.window.Window', {
            title: topic,
            width: vidWidth + 10,
            height: vidHeight + 41,
            html: '<div>' +
                    '<video style="max-width: 100%; max-height: 100%; width: 100%; height: 100%;" controls autoplay>' +
                        '<source src="bags/video?bagId=' + this.bagId + '&topic=' + topic + '&frameSkip=' + frameSkip + '">' +
                        'Your browser does not support embedded video.' +
                    '</video>' +
                  '</div>'
        });
        win.show();
    }
});