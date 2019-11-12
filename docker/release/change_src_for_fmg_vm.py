#!/usr/bin/python3
import sys
import difflib
import os
from lxml import etree as ET
import git


def unified_diff(fromfile, tofile):
    """
    Compare 'fromfile' and 'tofile'; generate the delta as a unified diff.
    """
    with open(fromfile, 'r') as origin:
        with open(tofile, 'r') as updated:
            diff = difflib.unified_diff(
                origin.readlines(),
                updated.readlines(),
                fromfile=fromfile,
                tofile=tofile
            )
            sys.stdout.writelines(diff)


def enable_cookie_http_transmit():
    """
     In BPJ VM Apache HTTPD configuration require PROJ docker uses HTTP
     for user who access PROJ via BPJ menual and uses HTTPS for user who access PROJ
     directly
    """

    deploy_des_path = 'project/src/main/webapp/WEB-INF'
    project_root = None
    try:
        project_root = git.Repo(
            ".", search_parent_directories=True).git.rev_parse('--show-toplevel')
    except git.InvalidGitRepositoryError as ex:
        sys.stdout.writelines(
            '{0}, Not in Git repository. In Docke container where the src is under / \n'.format(ex))
        project_root = '/'

    deploy_des = os.path.join(project_root, deploy_des_path, DEPLOY_DES)
    tree = ET.parse(deploy_des, ET.XMLParser(remove_blank_text=False))
    root = tree.getroot()
    secure = root.find('./session-config/cookie-config/secure')
    if secure is not None and secure.text == 'true':
        os.rename(deploy_des, (deploy_des+'.back'))
        secure.text = 'false'

        tree.write(deploy_des)
        unified_diff(deploy_des+'.back', deploy_des)
    else:
        sys.stdout.writelines("Did not update {}\nIt has expected content:\n{}\n".format(
            deploy_des,
            ET.tostring(root, pretty_print=True, encoding="unicode")))


def main():
    enable_cookie_http_transmit()


DEPLOY_DES = 'web.xml'
if __name__ == "__main__":
    main()
