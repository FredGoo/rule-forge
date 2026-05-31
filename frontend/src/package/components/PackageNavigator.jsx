import {useState, useEffect} from 'react';

/**
 * PackageNavigator: package-centric navigation component.
 * Displays a dropdown of packages, version selector, and branch indicator.
 * Calls /packageeditor/loadPackageTree to get package file tree.
 */
export default function PackageNavigator({project, onFileSelect, onVersionChange}) {
    const [packages, setPackages] = useState([]);
    const [selectedPackage, setSelectedPackage] = useState(null);
    const [versions, setVersions] = useState([]);
    const [currentVersion, setCurrentVersion] = useState(null);
    const [resourceItems, setResourceItems] = useState([]);
    const [branches, setBranches] = useState([]);
    const [loading, setLoading] = useState(false);

    // Load packages for the project
    useEffect(() => {
        if (!project) return;
        setLoading(true);
        const url = window._server + '/packageeditor/loadPackages';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(r => {
            if (!r.ok) throw r;
            return r.json();
        }).then(data => {
            setPackages(data || []);
            setLoading(false);
        }).catch(err => {
            console.error('Failed to load packages:', err);
            setLoading(false);
        });
    }, [project]);

    // Load branches
    useEffect(() => {
        if (!project) return;
        const url = window._server + '/packageeditor/listBranches';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(r => {
            if (!r.ok) throw r;
            return r.json();
        }).then(data => {
            setBranches(data.branches || []);
        }).catch(err => {
            console.error('Failed to load branches:', err);
        });
    }, [project]);

    function loadPackageTree(packageId, version) {
        if (!project || !packageId) return;
        setLoading(true);
        const url = window._server + '/packageeditor/loadPackageTree';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                project,
                packageId,
                version: version || ''
            }).toString()
        }).then(r => {
            if (!r.ok) throw r;
            return r.json();
        }).then(data => {
            setVersions(data.versions || []);
            setCurrentVersion(data.currentVersion);
            setResourceItems(data.resourceItems || []);
            setLoading(false);
            if (onVersionChange) {
                onVersionChange(data.currentVersion, data.gitTag);
            }
        }).catch(err => {
            console.error('Failed to load package tree:', err);
            setLoading(false);
        });
    }

    function handlePackageSelect(e) {
        const packageId = e.target.value;
        const pkg = packages.find(p => p.id === packageId);
        setSelectedPackage(pkg);
        if (packageId) {
            loadPackageTree(packageId);
        } else {
            setVersions([]);
            setResourceItems([]);
            setCurrentVersion(null);
        }
    }

    function handleVersionSelect(e) {
        const version = e.target.value;
        if (selectedPackage) {
            loadPackageTree(selectedPackage.id, version);
        }
    }

    function handleFileClick(item) {
        if (onFileSelect) {
            onFileSelect({
                path: item.path,
                name: item.name || item.path.split('/').pop(),
                version: item.version,
                gitTag: item.gitTag
            });
        }
    }

    const currentBranch = branches.find(b => b.startsWith('user/'));
    const branchName = currentBranch ? currentBranch : 'main';

    return (
        <div className="package-navigator">
            {/* Branch indicator */}
            <div className="branch-indicator" style={{
                padding: '4px 8px',
                background: branchName !== 'main' ? '#fff3cd' : '#d4edda',
                borderBottom: '1px solid #dee2e6',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
            }}>
                <i className="rf rf-branch" style={{fontSize: '14px'}}/>
                <span>当前分支: <strong>{branchName}</strong></span>
                {branchName !== 'main' && (
                    <span style={{color: '#856404', marginLeft: '8px'}}>(与 main 的修改将标黄)</span>
                )}
            </div>

            {/* Package selector */}
            <div style={{padding: '8px', borderBottom: '1px solid #dee2e6'}}>
                <label style={{fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px'}}>
                    知识包
                </label>
                <select className="form-control" value={selectedPackage?.id || ''} onChange={handlePackageSelect}>
                    <option value="">选择知识包...</option>
                    {packages.map(pkg => (
                        <option key={pkg.id} value={pkg.id}>{pkg.name || pkg.id}</option>
                    ))}
                </select>
            </div>

            {/* Version selector */}
            {selectedPackage && versions.length > 0 && (
                <div style={{padding: '8px', borderBottom: '1px solid #dee2e6'}}>
                    <label style={{fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px'}}>
                        版本
                    </label>
                    <select className="form-control" value={currentVersion || ''} onChange={handleVersionSelect}>
                        {versions.map(v => (
                            <option key={v.version} value={v.version}>
                                {v.version} ({v.auditStatus === 90 ? '已审批' : v.auditStatus === 20 ? '审批中' : '待审批'})
                                {' - ' + (v.createUser || '')}
                            </option>
                        ))}
                    </select>
                </div>
            )}

            {/* File list */}
            {selectedPackage && resourceItems.length > 0 && (
                <div style={{padding: '8px'}}>
                    <label style={{fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px'}}>
                        决策文件
                    </label>
                    <ul style={{listStyle: 'none', padding: 0, margin: 0}}>
                        {resourceItems.map((item, idx) => (
                            <li key={idx}
                                style={{
                                    padding: '4px 8px',
                                    cursor: 'pointer',
                                    borderBottom: '1px solid #f0f0f0',
                                    fontSize: '13px'
                                }}
                                onClick={() => handleFileClick(item)}
                                onMouseEnter={e => e.target.style.background = '#f5f5f5'}
                                onMouseLeave={e => e.target.style.background = ''}
                            >
                                <i className="rf rf-file" style={{marginRight: '6px'}}/>
                                {item.name || item.path.split('/').pop()}
                                {item.version && (
                                    <span style={{color: '#999', marginLeft: '8px', fontSize: '11px'}}>
                                        v{item.version}
                                    </span>
                                )}
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            {loading && (
                <div style={{padding: '20px', textAlign: 'center', color: '#999'}}>
                    加载中...
                </div>
            )}
        </div>
    );
}
