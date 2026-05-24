/**
 * HighlightCell - Base cell with highlight/selection behavior.
 *
 * Extends BaseCell with click and right-click handlers that position
 * a highlight div over the cell to indicate the active selection.
 *
 * Extracted from the complexScoreCard webpack bundle (module 317).
 */

import BaseCell from './BaseCell.js';

export default class HighlightCell extends BaseCell {
    /**
     * @param {Object} rowContext - The row context providing table reference
     */
    constructor(rowContext) {
        super(rowContext);

        const complexTable = rowContext.complexTable;
        const self = this;
        const highlightHandler = function () {
            const tdEl = this;
            const width = tdEl.clientWidth;
            const height = tdEl.clientHeight;
            const highlightDiv = complexTable.getHighlightDiv();
            highlightDiv.style.width = width + 'px';
            highlightDiv.style.height = height + 'px';
            tdEl.prepend(highlightDiv);
            highlightDiv.currentTD = tdEl;
            tdEl.addEventListener('DOMSubtreeModified', function () {
                const w = tdEl.clientWidth;
                const h = tdEl.clientHeight;
                highlightDiv.style.width = w + 'px';
                highlightDiv.style.height = h + 'px';
            });
        };
        this.td.addEventListener('click', highlightHandler);
        this.td.addEventListener('contextmenu', highlightHandler);
    }
}
