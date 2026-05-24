import Cell from './Cell.js';
export default class CustomCell extends Cell{
    constructor(row,col,cellData){
        super(row,col,cellData);
        this.type="custom";
    }
    initCell(cellData){
        const container = document.createElement('div');
        this.inputType=new ruleforge.InputType(null,"无");
        container.appendChild(this.inputType.getContainer());
        if(cellData && cellData.value){
            const value=cellData.value;
            this.inputType.setValueType(value.valueType,value);
        }
        this.td.appendChild(container);
    }
}
