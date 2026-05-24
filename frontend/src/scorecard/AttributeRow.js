import AttributeCell from './AttributeCell.js';
import Row from './Row.js';
import ConditionRow from './ConditionRow.js';
import CustomCell from './CustomCell.js';

export default class AttributeRow extends Row{
    constructor(table,rowData){
        super(table);
        this.rowData=rowData;
        this.conditionRows=[];
        this.tr = document.createElement('tr');
        this.tr.style.cssText = 'min-height: 25px';
        this.tr.appendChild(this.newAttributeCell());
        this.tr.appendChild(this.newConditionCell());
        this.tr.appendChild(this.newScoreCell());
        this.initCustomCells();
    }
    addCustomCol(customCol){
        const cell=new CustomCell(this,customCol);
        this.tr.appendChild(cell.td);
        customCol.customCells.push(cell);
        for(let row of this.conditionRows){
            row.addCustomCol(customCol);
        }
    }

    removeCustomCol(customCol){
        let posArray=[];
        for(let i=0;i<this.cells.length;i++){
            let cell=this.cells[i];
            if(cell.col===customCol){
                posArray.push(i);
            }
        }
        for(let pos of posArray){
            this.cells.splice(pos,1);
        }
        for(let row of this.conditionRows){
            row.removeCustomCol(customCol);
        }
    }

    initConditionRows(rowData){
        if(!rowData)return;
        const conditionRows=rowData.conditionRows || [];
        for(let conditionRowData of conditionRows){
            this.addConditionRow(conditionRowData);
        }
    }
    newAttributeCell() {
        let cellData=null;
        if(this.rowData){
            cellData=this.scoreCardTable.getCell(this.rowData.rowNumber,1);
        }
        this.attributeCell = new AttributeCell(this,this.scoreCardTable.attributeCol,cellData);
        return this.attributeCell.td;
    }
    remove(){
        const pos=this.scoreCardTable.attributeRows.indexOf(this);
        this.scoreCardTable.attributeRows.splice(pos,1);
        for(let row of this.conditionRows){
            row.tr.remove();
        }
        this.tr.remove();
    }
    removeConditionRow(conditionRow){
        const pos=this.conditionRows.indexOf(conditionRow);
        this.conditionRows.splice(pos,1);
        let rowSpan=this.attributeCell.td.rowSpan;
        if(!rowSpan){
            rowSpan=0;
        }else{
            rowSpan=parseInt(rowSpan)-1;
        }
        this.attributeCell.td.rowSpan=rowSpan;
        conditionRow.tr.remove();
    }
    addConditionRow(conditionRowData){
        const newConditionRow=new ConditionRow(this.scoreCardTable,this,conditionRowData);
        let rowSpan=this.attributeCell.td.rowSpan;
        if(!rowSpan){
            rowSpan=2;
        }else{
            rowSpan=parseInt(rowSpan)+1;
        }
        this.attributeCell.td.rowSpan=rowSpan;
        if(this.conditionRows.length>0){
            this.conditionRows[this.conditionRows.length-1].tr.after(newConditionRow.tr);
        }else{
            this.tr.after(newConditionRow.tr);
        }
        this.conditionRows.push(newConditionRow);
    }
    toXml(){
        let xml="<attribute-row row-number=\""+this.getRowNumber()+"\">";
        for(let row of this.conditionRows){
            xml+=row.toXml();
        }
        xml+="</attribute-row>";
        return xml;
    }
}
