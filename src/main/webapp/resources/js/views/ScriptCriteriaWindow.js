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

Ext.define('BagDatabase.views.ScriptCriteriaWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.scriptCriteriaWindow',
    layout: 'fit',
    title: 'Criteria Editor',
    width: 500,
    height: 400,
    constrainHeader: true,
    items: [{
        xtype: 'form',
        bodyPadding: 5,
        itemId: 'scriptCriteriaForm',
        defaultType: 'textfield',
        layout: {
            type: 'vbox',
            align: 'stretch'
        },
        defaults: {
            labelWidth: 140,
            width: '100%'
        },
        items: [{
            xtype: 'hidden',
            name: 'id'
        }, {
            xtype: 'hidden',
            name: 'scriptId'
        }, {
            xtype: 'displayfield',
            hideLabel: true,
            labelWidth: 0,
            value: 'All of these conditions must be true for this set of criteria to match; blank fields are ignored.'
        }, {
            fieldLabel: 'Directory',
            name: 'directory',
            tooltipHtml: 'A regular expression that will be matched against the directory containing the bag'
        }, {
            fieldLabel: 'Filename',
            name: 'filename',
            tooltipHtml: 'A regular expression that will be matched against the bag\'s filename'
        }, {
            fieldLabel: 'Message Types',
            name: 'messageTypes',
            tooltipHtml: 'A comma-separated list of ROS message types that must exist in the bag'
        }, {
            fieldLabel: 'Topic Names',
            name: 'topicNames',
            tooltipHtml: 'A comma-separated list of ROS topic names that must exist in the bag'
        }],
        listeners: {
            afterRender: function() {
                var tips, fields;
                tips = [];
                fields = this.query('field');
                fields.forEach(function(field) {
                    if (field.config.tooltipHtml) {
                        tips.push(new Ext.tip.ToolTip({
                            target: field.el,
                            width: 200,
                            html: field.config.tooltipHtml,
                            trackMouse: true
                        }));
                    }
                });
                this.tips = tips;
            },
            destroy: function() {
                this.tips = Ext.destroy(this.tips);
            }
        }
    }]

});
