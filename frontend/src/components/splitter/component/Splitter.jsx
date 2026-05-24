import '../css/jquery.splitter.css';
import React, {Component} from 'react';

export default class Splitter extends Component {
    constructor(props) {
        super(props);
        var position = this._parsePosition(props.position || '50%');
        this.state = {position: position};
        this.containerRef = React.createRef();
        this._dragging = false;
        this._onMouseDown = this._onMouseDown.bind(this);
        this._onMouseMove = this._onMouseMove.bind(this);
        this._onMouseUp = this._onMouseUp.bind(this);
        this._onResize = this._onResize.bind(this);
    }

    _parsePosition(position) {
        if (typeof position === 'number') {
            return position;
        } else if (typeof position === 'string') {
            var match = position.match(/^([0-9\.]+)(px|%)$/);
            if (match) {
                if (match[2] === 'px') {
                    return parseFloat(match[1]);
                } else {
                    // Percentage will be resolved later in componentDidMount
                    return position;
                }
            }
        }
        return position;
    }

    _resolvePosition(position, size) {
        if (typeof position === 'number') {
            return position;
        } else if (typeof position === 'string') {
            var match = position.match(/^([0-9\.]+)%$/);
            if (match) {
                return (size * parseFloat(match[1])) / 100;
            }
            match = position.match(/^([0-9\.]+)px$/);
            if (match) {
                return parseFloat(match[1]);
            }
        }
        return position;
    }

    _getLimit() {
        return parseInt(this.props.limit, 10) || 100;
    }

    componentDidMount() {
        var domNode = this.containerRef.current;
        if (!domNode) return;
        domNode.style.height = window.innerHeight + 'px';

        var size = this._isVertical() ? domNode.offsetWidth : domNode.offsetHeight;
        var resolved = this._resolvePosition(this.state.position, size);
        var limit = this._getLimit();
        resolved = Math.max(resolved, limit);
        resolved = Math.min(resolved, size - limit);
        this.setState({position: resolved});

        window.addEventListener('resize', this._onResize);
    }

    componentWillUnmount() {
        window.removeEventListener('resize', this._onResize);
        document.removeEventListener('mousemove', this._onMouseMove);
        document.removeEventListener('mouseup', this._onMouseUp);
    }

    _isVertical() {
        return this.props.orientation === 'vertical';
    }

    _onResize() {
        var domNode = this.containerRef.current;
        if (!domNode) return;
        domNode.style.height = window.innerHeight + 'px';

        var size = this._isVertical() ? domNode.offsetWidth : domNode.offsetHeight;
        var limit = this._getLimit();
        var pos = this.state.position;
        if (typeof pos === 'string') {
            pos = this._resolvePosition(pos, size);
        }
        pos = Math.max(pos, limit);
        pos = Math.min(pos, size - limit);
        this.setState({position: pos});
    }

    _onMouseDown(e) {
        e.preventDefault();
        this._dragging = true;
        this._startPos = this.state.position;
        this._startClient = this._isVertical() ? e.clientX : e.clientY;
        document.addEventListener('mousemove', this._onMouseMove);
        document.addEventListener('mouseup', this._onMouseUp);
        document.body.style.cursor = this._isVertical() ? 'col-resize' : 'row-resize';
        document.body.style.userSelect = 'none';
    }

    _onMouseMove(e) {
        if (!this._dragging) return;
        var domNode = this.containerRef.current;
        if (!domNode) return;

        var limit = this._getLimit();
        var size = this._isVertical() ? domNode.offsetWidth : domNode.offsetHeight;
        var client = this._isVertical() ? e.clientX : e.clientY;
        var rect = domNode.getBoundingClientRect();
        var offset = this._isVertical() ? rect.left : rect.top;
        var pos = client - offset;

        pos = Math.max(pos, limit);
        pos = Math.min(pos, size - limit);
        this.setState({position: pos});
    }

    _onMouseUp() {
        this._dragging = false;
        document.removeEventListener('mousemove', this._onMouseMove);
        document.removeEventListener('mouseup', this._onMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
    }

    render() {
        var children = React.Children.toArray(this.props.children);
        var first = children[0];
        var second = children[1];
        var isVertical = this._isVertical();
        var position = this.state.position;
        var dividerSize = 7;

        var firstStyle, secondStyle, dividerStyle;
        if (isVertical) {
            firstStyle = {
                position: 'absolute',
                top: 0,
                left: 0,
                height: '100%',
                width: (typeof position === 'number' ? (position - dividerSize / 2) : position),
                overflow: 'auto'
            };
            secondStyle = {
                position: 'absolute',
                top: 0,
                right: 0,
                height: '100%',
                width: (typeof position === 'number' ? ('calc(100% - ' + (position + dividerSize / 2) + 'px)') : '50%'),
                overflow: 'auto'
            };
            dividerStyle = {
                position: 'absolute',
                top: 0,
                height: '100%',
                width: dividerSize + 'px',
                left: (typeof position === 'number' ? (position - dividerSize / 2) + 'px' : '50%'),
                borderLeft: 'solid 1px #999',
                borderRight: 'solid 1px #999',
                backgroundColor: '#f5f5f5',
                cursor: 'col-resize',
                zIndex: 900
            };
        } else {
            firstStyle = {
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: (typeof position === 'number' ? (position - dividerSize / 2) : position),
                overflow: 'auto'
            };
            secondStyle = {
                position: 'absolute',
                left: 0,
                bottom: 0,
                width: '100%',
                height: (typeof position === 'number' ? ('calc(100% - ' + (position + dividerSize / 2) + 'px)') : '50%'),
                overflow: 'auto'
            };
            dividerStyle = {
                position: 'absolute',
                left: 0,
                width: '100%',
                height: dividerSize + 'px',
                top: (typeof position === 'number' ? (position - dividerSize / 2) + 'px' : '50%'),
                backgroundColor: '#5F5F5F',
                cursor: 'row-resize',
                zIndex: 800
            };
        }

        return (
            <div ref={this.containerRef} style={{position: 'relative'}}>
                <div style={firstStyle}>{first}</div>
                <div style={dividerStyle} onMouseDown={this._onMouseDown}></div>
                <div style={secondStyle}>{second}</div>
            </div>
        );
    }
}
