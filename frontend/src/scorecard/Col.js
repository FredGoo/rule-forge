export default class Col {
    constructor(table) {
        this.scoreCardTable = table;
    }

    buildColResizeTrigger() {
        const resizeTrigger = document.createElement('span');
        resizeTrigger.className = 'col-resize-trigger';
        resizeTrigger.innerHTML = '&nbsp;';
        this.resizeTrigger = resizeTrigger;
        return resizeTrigger;
    }

    getColNumber() {
        switch (this.type) {
            case "attribute":
                return 1;
            case "condition":
                return 2;
            case "score":
                return 3;
        }
        const pos = this.scoreCardTable.customCols.indexOf(this);
        return pos + 4;
    }

    bindColResize() {
        let resizeStart = false, resizeTargetCol, resizeStartX, resizeStartWidth;
        const _this = this;
        this.resizeTrigger.addEventListener('mousedown', function (e) {
            resizeTargetCol = this.parentElement;
            resizeStart = true;
            resizeStartX = e.pageX;
            resizeStartWidth = resizeTargetCol.clientWidth;
            e.preventDefault();
        });
        document.addEventListener('mousemove', function (e) {
            if (resizeStart) {
                const newWidth = resizeStartWidth + (e.pageX - resizeStartX);
                _this.width = newWidth;
                resizeTargetCol.style.width = newWidth + 'px';
                e.preventDefault();
            }
        });
        document.addEventListener('mouseup', function (e) {
            resizeStart = false;
            window._setDirty();
        });
    }
}
