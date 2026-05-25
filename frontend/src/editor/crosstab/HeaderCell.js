/**
 * HeaderCell - The top-left corner cell of the crosstab grid.
 *
 * Manages the header cell with editable text, rowspan/colspan adjustments,
 * and XML serialization. Displayed at the intersection of the first top row
 * and first left column.
 *
 * Extracted from the crosstab webpack bundle (module 332).
 */

export default class HeaderCell {
    /**
     * @param {TopRow} row - The first top row
     * @param {LeftColumn} col - The first left column
     */
    constructor(row, col) {
        this.row = row;
        this.col = col;
        const td = document.createElement('td');
        td.style.cssText = 'text-align:center;vertical-align: middle;background: #f8f8f8;color: #4caf50;border: 1px solid #c6c4c4;';
        this.td = td;
        this.init();
    }

    /**
     * Initialize the header cell DOM: display label, text editor, and edit button.
     */
    init() {
        this.row.tr.appendChild(this.td);

        this.text = 'TOP/LEFT';
        const label = document.createElement('span');
        label.textContent = 'TOP/LEFT';
        this.label = label;
        this.td.appendChild(label);

        const editor = document.createElement('textarea');
        editor.rows = 3;
        editor.className = 'form-control';
        editor.style.cssText = 'display: none;padding: 1px;';
        this.editor = editor;
        this.td.appendChild(editor);

        const editorButton = document.createElement('div');
        editorButton.style.cssText = 'margin-left: 5px;cursor:pointer';
        editorButton.title = '编辑';
        editorButton.innerHTML = '<i class="glyphicon glyphicon-edit"></i>';
        this.editorButton = editorButton;
        this.td.appendChild(editorButton);

        const self = this;
        editorButton.addEventListener('click', function () {
            self.editor.style.display = '';
            self.editor.value = self.text;
            self.label.style.display = 'none';
            self.editorButton.style.display = 'none';
            self.editor.focus();
        });

        editor.addEventListener('blur', function () {
            self.editor.style.display = 'none';
            self.label.style.display = '';
            let displayText = self.text || '';
            displayText = displayText.replace(new RegExp('\n', 'gm'), '<br>');
            self.label.innerHTML = displayText;
            self.editorButton.style.display = '';
        });

        editor.addEventListener('change', function () {
            self.text = this.value;
            window._setDirty();
        });
    }

    /**
     * Initialize header cell data from server response.
     *
     * @param {Object} data - Header cell data
     * @param {string} [data.text] - Display text
     * @param {number} [data.rowspan] - Row span
     * @param {number} [data.colspan] - Column span
     */
    initData(data) {
        if (data) {
            let text = data.text;
            if (text) {
                text = text.replace(new RegExp('\n', 'gm'), '<br>');
                this.text = text;
                this.label.innerHTML = text;
            }
            const rowspan = data.rowspan;
            if (rowspan) {
                this.td.rowSpan = rowspan;
            }
            const colspan = data.colspan;
            if (colspan) {
                this.td.colSpan = colspan;
            }
        }
    }

    /**
     * Adjust the colspan of this header cell.
     * @param {boolean} increment - True to increment, false to decrement
     */
    adjustColSpan(increment) {
        let colspan = this.td.colSpan;
        colspan || (colspan = 1);
        if (increment) {
            colspan++;
        } else {
            colspan--;
        }
        colspan || (colspan = 1);
        this.td.colSpan = colspan;
    }

    /**
     * Adjust the rowspan of this header cell.
     * @param {boolean} increment - True to increment, false to decrement
     */
    adjustRowSpan(increment) {
        let rowspan = this.td.rowSpan;
        rowspan || (rowspan = 1);
        if (increment) {
            rowspan++;
        } else {
            rowspan--;
        }
        rowspan || (rowspan = 1);
        this.td.rowSpan = rowspan;
    }

    /**
     * Get the current rowspan value.
     * @returns {number}
     */
    getRowSpan() {
        let rowspan = this.td.rowSpan;
        rowspan || (rowspan = 1);
        return parseInt(rowspan);
    }

    /**
     * Get the current colspan value.
     * @returns {number}
     */
    getColSpan() {
        let colspan = this.td.colSpan;
        colspan || (colspan = 1);
        return parseInt(colspan);
    }

    /**
     * Serialize this header cell to XML.
     * @returns {string} XML representation
     */
    toXml() {
        return '<header rowspan="' + this.getRowSpan() + '" colspan="' + this.getColSpan() + '"><![CDATA[' + this.text + ']]></header>';
    }
}
