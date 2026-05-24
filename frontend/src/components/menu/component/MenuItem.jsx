import React,{Component} from 'react';

export default class MenuItem extends Component{
    render(){
        const {item,data,dispatch}=this.props;
        return (
            <li onClick={item.click ? () => item.click(data,dispatch) : undefined}>
                <a href='###'><i className={item.icon} style={{color:'#00A0E8'}}></i> {item.name}</a>
            </li>
        );
    }
}
