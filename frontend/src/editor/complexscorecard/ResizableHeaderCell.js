/**
 * ResizableHeaderCell - Base header cell with column resize behavior.
 *
 * Extends BaseCell with a drag handle for resizing columns and
 * logic for updating column width and rebuilding the highlight div.
 *
 * Extracted from the complexScoreCard webpack bundle (module 319).
 */

import BaseCell from './BaseCell.js';

export default class ResizableHeaderCell extends BaseCell {
    /**
     * @param {Object} rowContext - The row context providing table reference
     */
    constructor(rowContext) {
        super(rowContext);
        this.td.style.paddingRight = '0';
        this.td.appendChild(this.buildColResizeTrigger());
        this.bindColResize(rowContext);
    }

    /**
     * Build the drag handle element for column resizing.
     * @returns {HTMLElement} The resize trigger span
     */
    buildColResizeTrigger() {
        const resizeTrigger = document.createElement('span');
        resizeTrigger.style.cssText = 'cursor: col-resize;width: 3px;height: 20px;float: right;border: solid 2px transparent;';
        resizeTrigger.innerHTML = '&nbsp;';
        this.resizeTrigger = resizeTrigger;
        return resizeTrigger;
    }

    /**
     * Bind mouse events for column resize drag behavior.
     *
     * @param {Object} rowContext - The row context providing table reference
     */
    bindColResize(rowContext) {
        let isDragging = false;
        let parentTd;
        let startX;
        let startWidth;
        const self = this;

        this.resizeTrigger.addEventListener('mouseover', function () {
            this.style.border = 'solid 2px #999';
        });
        this.resizeTrigger.addEventListener('mouseout', function () {
            this.style.border = 'solid 2px transparent';
        });
        this.resizeTrigger.addEventListener('mousedown', function (e) {
            parentTd = this.parentElement;
            isDragging = true;
            startX = e.pageX;
            startWidth = parentTd.clientWidth;
            e.preventDefault();
        });
        document.addEventListener('mousemove', function (e) {
            if (isDragging) {
                const newWidth = startWidth + (e.pageX - startX);
                if (self.actionCol) {
                    self.actionCol.width = newWidth;
                } else {
                    self.conditionCol.width = newWidth;
                }
                parentTd.style.width = newWidth + 'px';
                self._rebuildHighLightDiv(rowContext);
                e.preventDefault();
            }
        });
        document.addEventListener('mouseup', function () {
            isDragging = false;
            window._setDirty();
        });
    }

    /**
     * Rebuild the highlight div after column resize.
     *
     * @param {Object} rowContext - The row context providing table reference
     */
    _rebuildHighLightDiv(rowContext) {
        const highlightDiv = rowContext.complexTable.getHighlightDiv();
        const currentTD = highlightDiv.currentTD;
        if (currentTD) {
            const width = currentTD.clientWidth;
            const height = currentTD.clientHeight;
            highlightDiv.style.width = width + 'px';
            highlightDiv.style.height = height + 'px';
        }
    }
}
