"""Publish an existing, checked Release using the workflow token (no second tag build)."""
import json
import os
import re
import subprocess
from pathlib import Path


def command(*args):
    return subprocess.check_output(args, text=True)


def gh_json(*args):
    return json.loads(command("gh", *args))


tag = os.environ["RELEASE_TAG"]
source = os.environ["SOURCE_COMMIT"]
assert re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag), "Invalid release tag"
assert re.fullmatch(r"[0-9a-f]{40}", source), "An exact source commit is required"
build = command("git", "show", f"{source}:android/app/build.gradle.kts")
assert re.search(r'versionName\s*=\s*"' + re.escape(tag[1:]) + r'"', build), "Tag must match the application version"

# Documentation may be completed after verification; compiled inputs must stay identical.
subprocess.run([
    "git", "diff", "--exit-code", source, "HEAD", "--", "android/app",
    "android/build.gradle.kts", "android/settings.gradle.kts", "android/gradle.properties", "android/gradle",
], check=True, stdout=subprocess.DEVNULL)

for key, workflow in (("DEBUG_RUN", "Android app"), ("RELEASE_RUN", "Android signed release")):
    run = os.environ[key]
    assert run.isdigit(), "Invalid workflow run ID"
    result = gh_json("run", "view", run, "--json", "workflowName,status,conclusion,headSha")
    assert result["workflowName"] == workflow and result["headSha"] == source, f"{workflow}: wrong source"
    assert result["status"] == "completed" and result["conclusion"] == "success", f"{workflow}: verification incomplete"

release = gh_json("release", "view", tag, "--json", "tagName,isDraft,targetCommitish,assets")
assert release["tagName"] == tag and release["targetCommitish"] == source, "Release must target the verified source"
required = {
    f"StudyWithMiku-Android-{tag[1:]}-release.apk",
    "SHA256SUMS.txt", "SIGNING-CERTIFICATE.txt", "Android-release-check-reports.zip",
}
assets = {asset["name"] for asset in release["assets"] if asset["size"] > 0}
assert required <= assets, "Verified release assets are missing"

if release["isDraft"]:
    subprocess.run([
        "gh", "release", "edit", tag, "--tag", tag, "--target", source,
        "--draft=false", "--prerelease=false", "--latest",
        "--title", f"Study With Miku Android {tag[1:]}", "--notes-file", ".github/release-notes.md",
    ], check=True)

published = gh_json("release", "view", tag, "--json", "isDraft,isPrerelease,publishedAt,url,targetCommitish")
assert not published["isDraft"] and not published["isPrerelease"] and published["publishedAt"], "Release was not publicly published"
assert published["targetCommitish"] == source
ref = gh_json("api", f"repos/{os.environ['GH_REPO']}/git/ref/tags/{tag}")["object"]
while ref["type"] == "tag":
    ref = gh_json("api", f"repos/{os.environ['GH_REPO']}/git/tags/{ref['sha']}")["object"]
assert ref["type"] == "commit" and ref["sha"] == source, "Published tag does not match the checked application"
print(published["url"])
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with Path(summary).open("a") as output:
        output.write(f"Published [{tag}]({published['url']}) from `{source}`.\n")
