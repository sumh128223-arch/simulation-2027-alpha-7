import json, struct, itertools
from pathlib import Path
import numpy as np

ROOT = Path(__file__).absolute().parent.parent

def bounds(path):
    data = path.read_bytes()
    length = struct.unpack_from('<I', data, 12)[0]
    doc = json.loads(data[20:20+length])
    points = []
    def visit(index, parent):
        node = doc['nodes'][index]
        if 'matrix' in node:
            local = np.array(node['matrix']).reshape(4,4).T
        else:
            x,y,z,w = node.get('rotation', [0,0,0,1])
            local = np.eye(4)
            local[:3,:3] = np.array([
                [1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
                [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
                [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]]) @ np.diag(node.get('scale',[1,1,1]))
            local[:3,3] = node.get('translation',[0,0,0])
        world = parent @ local
        if 'mesh' in node:
            for primitive in doc['meshes'][node['mesh']]['primitives']:
                accessor = doc['accessors'][primitive['attributes']['POSITION']]
                corners = np.array(list(itertools.product(*zip(accessor['min'],accessor['max']))))
                points.extend((world @ np.column_stack([corners,np.ones(8)]).T).T[:,:3])
        for child in node.get('children',[]):
            visit(child,world)
    for node in doc['scenes'][doc.get('scene',0)]['nodes']:
        visit(node,np.eye(4))
    points = np.array(points)
    return points.min(0), points.max(0)

if __name__ == '__main__':
    for name in ['ascope/assets/Robot_8011/model_0.glb','ascope/assets/Robot_8011/model_1.glb']:
        low, high = bounds(ROOT / name)
        print(name, '\n min:', low.round(6), '\n max:', high.round(6))
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(2,3,figsize=(12,8))
    for row,name in enumerate(['ascope/assets/Robot_8011/model_0.glb','ascope/assets/Robot_8011/model_1.glb']):
        data=(ROOT/name).read_bytes()
        n=struct.unpack_from('<I',data,12)[0]
        doc=json.loads(data[20:20+n])
        binary=data[28+n:]
        accessor=doc['accessors'][doc['meshes'][0]['primitives'][0]['attributes']['POSITION']]
        view=doc['bufferViews'][accessor['bufferView']]
        offset=view.get('byteOffset',0)+accessor.get('byteOffset',0)
        stride=view.get('byteStride',12)
        points=np.ndarray((accessor['count'],3),dtype='<f4',buffer=binary,offset=offset,strides=(stride,4))
        points=points[::max(1,len(points)//30000)]
        for col,(a,b) in enumerate([(0,1),(0,2),(1,2)]):
            ax=axes[row,col]
            ax.scatter(points[:,a],points[:,b],s=.1)
            ax.set_aspect('equal');ax.set_xlabel('XYZ'[a]);ax.set_ylabel('XYZ'[b]);ax.set_title(name)
    fig.tight_layout()
    fig.savefig(ROOT/'ascope/model-coordinate-views.png',dpi=150)
