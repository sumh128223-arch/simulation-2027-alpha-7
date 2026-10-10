"""Build layout for the actual AdvantageScope bundled with WPILib alpha7.

Checks the installed controller schema; never disguises an untested alpha7 version.
"""
import copy
import json
import pathlib
import struct
import uuid

root = pathlib.Path(__file__).resolve().parents[1]
asar = pathlib.Path(r'C:\Users\Public\wpilib\2027_alpha7\advantagescope\resources\app.asar')
with asar.open('rb') as stream:
    header = struct.unpack('<4I', stream.read(16))
    index = json.loads(stream.read(header[3]))

    def read_entry(path):
        entry = index
        for name in path.split('/'):
            entry = entry['files'][name]
        stream.seek(8 + header[1] + int(entry['offset']))
        return stream.read(entry['size']).decode('utf-8')

    package = json.loads(read_entry('package.json'))
    runtime = read_entry('bundles/hub.js')
    assert 'childOf: "robot"' in runtime
    assert 'case "component":' in runtime
    assert 'case "swerveModuleVelocities":' in runtime
    assert 'return this.fields;' in runtime

layout = json.loads((root / 'ascope/layout-8011-live.json').read_text(encoding='utf-8'))
layout['version'] = package['version']
hub = layout['hubs'][0]
hub.update(x=50, y=50, width=1400, height=900)
tabs = hub['state']['tabs']
field = next(t for t in tabs['tabs'] if t['type'] == 3)
old = field['controller']['sources']
robot = copy.deepcopy(next(s for s in old if s['type'] == 'robot'))
component = copy.deepcopy(next(s for s in old if s['type'] == 'component'))
component['options'] = {}
# Component and module velocities must directly follow their robot parent.
children = [component]
for source in old:
    if source['type'] == 'swerveStates':
        source = copy.deepcopy(source)
        source['type'] = 'swerveModuleVelocities'
        children.append(source)
field['controller']['sources'] = [robot, *children, *[
    copy.deepcopy(s) for s in old
    if s['type'] in ('gamePiece', 'trajectory') and s['visible']
]]
graph = next(t for t in tabs['tabs'] if t['type'] == 1)
prefix = 'NT:/AdvantageKit/RealOutputs/Simulation/'

def numeric(key, color):
    return dict(type='stepped', logKey=prefix + key, logType='Number', visible=True,
                options=dict(size='normal', color=color))

graph['title'] = 'LT RT / Intake Angle'
graph['controller']['leftSources'] = [numeric('Controls/LT', '#00cc66'), numeric('Controls/RT', '#ff6600')]
graph['controller']['rightSources'] = [numeric('Intake/AngleDeg', '#e5b31b')]
graph['controller']['leftLockedRange'] = [-0.05, 1.05]
graph['controller']['rightLockedRange'] = [-5, 105]
graph['controller']['showRobotMode'] = True
fields = [prefix + k for k in ('Enabled', 'Controls/LT', 'Controls/RT', 'Intake/Goal',
                               'Intake/RollerGoal', 'Intake/AngleDeg')]
table = dict(type=4, title='Controls / Intake', controller=fields,
             controllerUUID=uuid.uuid4().hex, renderer=None, controlsHeight=0)
tabs['tabs'] = [t for t in tabs['tabs'] if t['type'] == 0] + [field, graph, table]
tabs['selected'] = 1
hub['state']['sidebar']['expanded'] = ['/AdvantageKit', '/AdvantageKit/RealOutputs',
    '/AdvantageKit/RealOutputs/Simulation', '/AdvantageKit/RealOutputs/Simulation/Controls',
    '/AdvantageKit/RealOutputs/Simulation/Intake', '/AdvantageKit/RealOutputs/Visualization']
layout['satellites'] = []
target = root / 'ascope/layout-8011-alpha7.json'
target.write_text(json.dumps(layout, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
assert field['controller']['sources'][1]['type'] == 'component'
assert all(s['type'] != 'rotationOverride' for s in field['controller']['sources'])
assert len(table['controller']) == 6
print(f'Created: {target}\nActual bundled AdvantageScope: {package["version"]}')
