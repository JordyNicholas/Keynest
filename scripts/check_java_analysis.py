"""Initial source-metadata-only SpotBugs baseline; findings are not accepted by reporting them."""

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = ['libs/keynest-policy', 'libs/keynest-generate', 'libs/keynest-seal', 'apps/manager-api']

for module in MODULES:
  report = ROOT / module / 'target/spotbugsXml.xml'
  root = ET.parse(report).getroot()
  summary = root.find('FindBugsSummary')
  errors = root.find('Errors')
  print(module, 'classes', summary.get('total_classes'), 'findings', len(root.findall('BugInstance')),
        'errors', errors.get('errors'), 'missing_classes', errors.get('missingClasses'))
  for bug in root.findall('BugInstance'):
    source = bug.find('SourceLine')
    method = bug.find('Method')
    field = bug.find('Field')
    owner = bug.find('Class')
    print(module, bug.get('type'), bug.get('priority'), owner.get('classname'),
          'method', method.get('name') if method is not None else '',
          'field', field.get('name') if field is not None else '',
          'line', source.get('start') if source is not None else '')
