import React,{Component} from 'react';

export default class MenuItem extends Component{
    render(){
        const {item,data,dispatch}=this.props;
        return (
            <li>
                <a href='###' onClick={(e) => { e.preventDefault(); item.click && item.click(data, dispatch); }}>
                    <i className={item.icon} style={{color:'var(--rf-primary)'}}></i> {item.name}
                </a>
            </li>
        );
    }
}
