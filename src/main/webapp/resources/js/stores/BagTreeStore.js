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

/**
 * The "filterer" property was supposed to have been added to TreeStore in
 * ExtJS 6.0.1, but apparently it's not actually in the distributed version?
 * So, let's override the TreeStore here and actually implement it...
 */
Ext.define('Override.Ext.data.TreeStore', {
    override: 'Ext.data.TreeStore',

    filterer: 'topdown',
    
    doFilter: function(node) {
        this.filterNodes(node, this.getFilters().getFilterFn(), true);
    },
    /**
     * @private
     * Filters the passed node according to the passed function.
     *
     * If this TreeStore is configured with {@link #cfg-filterer filterer: 'bottomup'}, leaf
     * nodes are tested according to the function. Additionally, parent nodes are filtered in
     * if any descendant leaf nodes have passed the filter test.
     *
     * Otherwise a parent node which fails the test will terminate the branch and
     * descendant nodes which pass the filter test will be filtered out.
     */
    filterNodes: function(node, filterFn, parentVisible) {
        var me = this,
            bottomUpFiltering = me.filterer === 'bottomup',
            // MUST call filterFn first to avoid shortcutting if parentVisible is false.
            // filterFn may have side effects, so must be called on all nodes.
            match = filterFn(node) && parentVisible || (node.isRoot() && !me.getRootVisible()),
            childNodes = node.childNodes,
            len = childNodes && childNodes.length,
            i, matchingChildren;
  
        if (len) {
            for (i = 0; i < len; ++i) {
                // MUST call method first to avoid shortcutting boolean expression if matchingChildren is true
                matchingChildren = me.filterNodes(childNodes[i], filterFn, match || bottomUpFiltering) || matchingChildren;
            }
            if (bottomUpFiltering) {
                match = matchingChildren || match;
            }
        }
 
        node.set("visible", match, me._silentOptions);
        return match;
    }
});

Ext.define('BagDatabase.stores.BagTreeStore', {
    extend: 'Ext.data.TreeStore',
    model: 'BagDatabase.models.BagTreeNode',
    requires: [ 'BagDatabase.models.BagTreeNode' ],
    filterer: 'bottomup',
    proxy: {
        type: 'ajax',
        reader: 'json',
        url: 'bags/treenode'
    },
    sorters: [{
        property: 'filename',
        direction: 'ASC'
    }],
    lazyFill: true
});