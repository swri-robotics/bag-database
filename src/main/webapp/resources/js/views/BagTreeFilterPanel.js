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

Ext.define('BagDatabase.views.BagTreeFilterPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.bagTreeFilterPanel',
    layout: 'anchor',
    frame: true,
    items: [{
        xtype: 'panel',
        layout: 'hbox',
        anchor: '100%',
        items: [{
            xtype: 'textfield',
            fieldLabel: 'Filter by File Name',
            itemId: 'filterText',
            name: 'filterText',
            flex: 1,
            margin: 5,
            enableKeyEvents: true,
            labelWidth: 120,
            listeners: {
                specialkey: function(field, event) {
                    if (event.getKey() == event.ENTER) {
                        var vp = field.up('viewport');
                        var filterPanel = field.up('bagTreeFilterPanel');
                        var store = vp.down('#bagTreePanel').getStore();
                        var text = field.getValue();
                        store.clearFilter();
                        store.filter([{
                            filterFn: function(item) {
                                return !item.get('leaf') || item.get('filename').match(text);
                            }
                        }]);
                    }
                },
                afterrender: function(field) {
                    Ext.tip.QuickTipManager.register({
                        target: field.getId(),
                        text: 'Filters bag files and folders by name.'
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
                var vp = button.up('viewport');
                var filterPanel = button.up('bagTreeFilterPanel');
                var store = vp.down('#bagTreePanel').getStore();
                var text = vp.down('#filterText').getValue();
                store.clearFilter();
                store.filter([{
                    filterFn: function(item) {
                        return !item.get('leaf') || item.get('filename').match(text);
                    }
                }]);
            }
        }]
    }]
});