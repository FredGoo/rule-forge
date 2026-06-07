import React, {Component} from 'react';
import {Modal, Form, Input, Switch} from 'antd';
import {UserItem} from '@/api/client';

interface UserFormModalProps {
    visible: boolean;
    user: UserItem | null;
    onOk: (values: { username?: string; password?: string; isAdmin: boolean; canExport: boolean }) => void;
    onCancel: () => void;
}

interface UserFormModalState {
    confirmLoading: boolean;
}

/**
 * 创建/编辑用户 Modal — V5.15
 *
 * 创建时显示 username + password 必填;
 * 编辑时 username 只读,password 为空则不修改。
 */
export default class UserFormModal extends Component<UserFormModalProps, UserFormModalState> {
    state: UserFormModalState = {
        confirmLoading: false,
    };

    formRef = React.createRef<any>();

    componentDidUpdate(prevProps: UserFormModalProps) {
        if (this.props.visible && !prevProps.visible) {
            // Modal 打开时设置初始值
            setTimeout(() => {
                const form = this.formRef.current;
                if (!form) return;
                if (this.props.user) {
                    form.setFieldsValue({
                        username: this.props.user.username,
                        password: '',
                        isAdmin: this.props.user.isAdmin,
                        canExport: this.props.user.canExport,
                    });
                } else {
                    form.resetFields();
                }
            }, 0);
        }
    }

    handleOk = () => {
        const form = this.formRef.current;
        if (!form) return;
        form.validateFields().then((values: any) => {
            this.setState({confirmLoading: true});
            this.props.onOk(values);
            this.setState({confirmLoading: false});
        });
    };

    render() {
        const {visible, user, onCancel} = this.props;
        const {confirmLoading} = this.state;
        const isEdit = !!user;

        return (
            <Modal
                title={isEdit ? '编辑用户' : '新增用户'}
                open={visible}
                onOk={this.handleOk}
                onCancel={onCancel}
                confirmLoading={confirmLoading}
                destroyOnHidden
                width={480}
            >
                <Form ref={this.formRef} layout="vertical" preserve={false}>
                    <Form.Item
                        name="username"
                        label="用户名"
                        rules={isEdit ? [] : [{required: true, message: '请输入用户名'}]}
                    >
                        <Input disabled={isEdit} placeholder="请输入用户名"/>
                    </Form.Item>
                    <Form.Item
                        name="password"
                        label={isEdit ? '新密码 (留空不修改)' : '密码'}
                        rules={isEdit ? [] : [{required: true, message: '请输入密码'}]}
                    >
                        <Input.Password placeholder={isEdit ? '留空则不修改密码' : '请输入密码'}/>
                    </Form.Item>
                    <Form.Item name="isAdmin" label="管理员" valuePropName="checked">
                        <Switch/>
                    </Form.Item>
                    <Form.Item name="canExport" label="允许导出" valuePropName="checked">
                        <Switch/>
                    </Form.Item>
                </Form>
            </Modal>
        );
    }
}
