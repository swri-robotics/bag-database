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

Ext.define('BagDatabase.views.SearchPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.searchPanel',
    layout: 'anchor',
    frame: true,
    items: [{
        xtype: 'panel',
        layout: 'hbox',
        anchor: '100%',
        items: [{
            xtype: 'textfield',
            fieldLabel: 'Full Text Search',
            itemId: 'searchTerms',
            name: 'searchTerms',
            stateful: true,
            stateId: 'searchTerms',
            flex: 1,
            margin: 5,
            enableKeyEvents: true,
            listeners: {
                specialkey: function(field, event) {
                    if (event.getKey() == event.ENTER) {
                        var vp, store, fields;
                        vp = field.up('viewport');
                        store = vp.down('#bagGrid').getStore();
                        fields = vp.down('#searchFields').getValue();
                        store.filterBags(field.getValue(), fields);
                    }
                },
                afterrender: function(field) {
                    Ext.tip.QuickTipManager.register({
                        target: field.getId(),
                        text: 'Searches for text in any of the checked fields.<br><b>*</b> Searching Message Types and Topic Names at the same time may be slow!'
                    });
                    this.focus();
                }
            }
        }, {
            xtype: 'button',
            itemId: 'searchButton',
            text: 'Search',
            iconCls: 'magnifier-icon',
            margin: 5,
            handler: function(button) {
                var vp, store, terms, fields;
                vp = button.up('viewport');
                store = vp.down('#bagGrid').getStore();
                terms = vp.down('#searchTerms').getValue();
                fields = vp.down('#searchFields').getValue();
                store.filterBags(terms, fields);
            }
        }]
    }, {
        xtype: 'checkboxgroup',
        itemId: 'searchFields',
        stateful: true,
        stateId: 'gridSearchFields',
        anchor: '100%',
        columns: 8,
        // The checkboxgroup widget doesn't actually save the states of its
        // checkboxes by default, so we have to manually do that.
        stateEvents: ['change'],
        applyState: function(state) {
            this.setValue(state);
        },
        getState: function() {
            return this.getValue();
        },
        getErrors: function(value) {
            var errors, tnCheckbox, mtCheckbox;
            errors = [];
            tnCheckbox = this.down('#topicNameCheckbox');
            mtCheckbox = this.down('#messageTypeCheckbox');
            if (tnCheckbox.getValue() && mtCheckbox.getValue()) {
                errors.push('Searching both Message Types and Topic Names will be slow.');
            }
            return errors;
        },
        msgTarget: 'side',
        items: [{
            boxLabel: 'Filename',
            name: 'field',
            inputValue: 'filename',
            value: true,
            qtip: 'The name of the bag file.'
        }, {
            boxLabel: 'Description',
            name: 'field',
            inputValue: 'description',
            value: true,
            qtip: 'The textual description of the bag file.'
        }, {
            boxLabel: 'Tags',
            name: 'field',
            inputValue: 'tags',
            value: true,
            qtip: 'Additional Metadata.'
        }, {
            boxLabel: 'Path',
            name: 'field',
            inputValue: 'path',
            value: true,
            qtip: 'The path on disk to the bag file.'
        }, {
            boxLabel: 'Location',
            name: 'field',
            inputValue: 'location',
            value: true,
            qtip: 'The geographic location where the bag was recorded.'
        }, {
            boxLabel: 'Vehicle',
            name: 'field',
            inputValue: 'vehicle',
            value: true,
            qtip: 'The vehicle on which the bag was recorded.'
        }, {
            boxLabel: 'Message Types',
            itemId: 'messageTypeCheckbox',
            name: 'field',
            inputValue: 'messageType',
            value: true,
            qtip: 'All of the types of messages recorded in the bag file.'
        }, {
            boxLabel: 'Topic Names',
            itemId: 'topicNameCheckbox',
            name: 'field',
            inputValue: 'topicName',
            qtip: 'All of the topics published by the bag file.'
        }],
        listeners: {
            afterrender: function(panel) {
                var checkboxes = panel.getBoxes();
                checkboxes.forEach(function(box) {
                    if (box.qtip) {
                        Ext.tip.QuickTipManager.register({
                            target: box.id,
                            text: box.qtip
                        });
                    }
                });
            }
        }
    }]
});