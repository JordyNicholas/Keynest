import copy
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('inventory', Path(__file__).resolve().parents[1] / 'dependency_inventory.py')
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


class InventoryValidationTest(unittest.TestCase):
  def setUp(self):
    self.bom = {'bomFormat': 'CycloneDX', 'specVersion': '1.6',
                'metadata': {'component': {'name': 'sample', 'version': '1', 'bom-ref': 'root'}},
                'components': [{'name': 'dependency', 'bom-ref': 'dep', 'purl': 'pkg:npm/dependency@1'}],
                'dependencies': [{'ref': 'root', 'dependsOn': ['dep']}, {'ref': 'dep', 'dependsOn': []}]}

  def validate(self, bom=None, required=None):
    return inventory.validate_bom(bom if bom is not None else self.bom, 'sample', '1', required or {'pkg:npm/dependency@1'})

  def test_valid_inventory_is_not_a_vulnerability_result(self):
    self.assertEqual(self.validate(), {'components': 1, 'uniquePackageUrls': 1})

  def test_missing_source_dependency_is_rejected(self):
    with self.assertRaises(ValueError):
      self.validate(required={'pkg:npm/omitted@2'})

  def test_wrong_version_empty_and_missing_graph_are_rejected(self):
    for change in ('version', 'empty', 'graph'):
      bom = copy.deepcopy(self.bom)
      if change == 'version': bom['metadata']['component']['version'] = '2'
      if change == 'empty': bom['components'] = []
      if change == 'graph': bom.pop('dependencies')
      with self.subTest(change=change), self.assertRaises(ValueError):
        self.validate(bom)

  def test_dangling_or_repeated_graph_and_component_refs_are_rejected(self):
    for change in ('dangling', 'repeated_graph', 'repeated_component', 'no_purl'):
      bom = copy.deepcopy(self.bom)
      if change == 'dangling': bom['dependencies'][0]['dependsOn'].append('missing')
      if change == 'repeated_graph': bom['dependencies'].append(bom['dependencies'][0])
      if change == 'repeated_component': bom['components'].append(bom['components'][0])
      if change == 'no_purl': bom['components'][0].pop('purl')
      with self.subTest(change=change), self.assertRaises(ValueError):
        self.validate(bom)

  def test_lock_coordinates_preserve_scopes_nested_versions_and_dev_packages(self):
    lock = {'packages': {'': {'version': '1'}, 'node_modules/@scope/pkg': {'version': '2', 'dev': True},
                         'node_modules/outer/node_modules/@scope/pkg': {'version': '3'},
                         'node_modules/local': {'link': True}}}
    self.assertEqual(inventory.lock_coordinates(lock), {'pkg:npm/@scope/pkg@2', 'pkg:npm/@scope/pkg@3'})


if __name__ == '__main__':
  unittest.main()
