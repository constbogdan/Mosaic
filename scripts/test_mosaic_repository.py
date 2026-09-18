import unittest

import mosaic_repository as repository


class RepositoryIdentityTests(unittest.TestCase):
    def test_exact_transition_names_are_accepted(self):
        for value in repository.DOWNSTREAM_REPOSITORIES:
            with self.subTest(value=value):
                self.assertEqual(value, repository.authenticate_downstream_repository(value))

    def test_lookalikes_forks_case_variants_and_malformed_values_are_rejected(self):
        for value in (
            "constbogdan/Mosaic2",
            "other/Mosaic",
            "constbogdan/wholphin",
            "fork/Wholphin",
            "constbogdan",
            "",
            None,
        ):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    repository.authenticate_downstream_repository(value)

    def test_workflow_ref_is_bound_to_the_authenticated_current_name(self):
        workflow = ".github/workflows/ci.yml"
        for value in repository.DOWNSTREAM_REPOSITORIES:
            with self.subTest(value=value):
                env = {
                    "GITHUB_REPOSITORY": value,
                    "GITHUB_WORKFLOW_REF": f"{value}/{workflow}@refs/heads/main",
                }
                self.assertEqual(
                    value,
                    repository.authenticate_workflow_repository(env, workflow),
                )
                other = next(item for item in repository.DOWNSTREAM_REPOSITORIES if item != value)
                env["GITHUB_WORKFLOW_REF"] = f"{other}/{workflow}@refs/heads/main"
                with self.assertRaises(ValueError):
                    repository.authenticate_workflow_repository(env, workflow)


if __name__ == "__main__":
    unittest.main()
