import Row from './Row.js';
import CustomCell from './CustomCell.js';
export default class ConditionRow extends Row{
    constructor(table,attributeRow,rowData){
        super(table);
        this.attributeRow=attributeRow;
        this.rowData=rowData;
        this.rowType="condition";
        this.tr = document.createElement('tr');
        this.tr.style.cssText = 'min-height: 25px';
        this.tr.appendChild(this.newConditionCell());
        this.tr.appendChild(this.newScoreCell());
        this.initCustomCells();
    }
    addCustomCol(customCol){
        const cell=new CustomCell(this,customCol);
        customCol.customCells.push(cell);
        this.tr.appendChild(cell.td);
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
    }

    remove(){
        this.attributeRow.removeConditionRow(this);
    }
    toXml(){
        let xml="<condition-row row-number=\""+this.getRowNumber()+"\">";
        xml+="</condition-row>";
        return xml;
    }
}
