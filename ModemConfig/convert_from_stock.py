#! /usr/bin/env python3

import sys
import os
import os.path
import argparse
from pathlib import Path


from androguard.core.bytecodes.apk import APK
from androguard.core.bytecodes.axml import AXMLPrinter, AXMLParser, END_DOCUMENT


parser = argparse.ArgumentParser(
    description="Extract operator mappings to modem firmware from stock OEM overlays"
)
parser.add_argument("oem_dir", type=Path, help="oem root dir containing the overlay")
parser.add_argument(
    "mcfg_dir",
    nargs="?",
    type=Path,
    help="Config directory containing mcfg_sw. Usually firmware/image/modem_pr/mcfg/configs/. Currently only used to print an overview of unreferenced files by the oem config",
)
parser.add_argument(
    "--overlay",
    type=Path,
    default="overlay/com.sonymobile.xperiasystemserver-res-305.apk",
    help="Path to the overlay within the oem directory",
)
parser.add_argument(
    "--add-paths",
    default=False,
    action="store_true",
    help="Add <path> elements containing the mbn path, as extracted from modem.conf files",
)
parser.add_argument(
    "--output",
    type=Path,
    default=Path(__file__).parent / 'res/xml/service_provider_sim_configs.xml',
    help="Write resulting ",
)
args = parser.parse_args()

oem_modem_config_dir = args.oem_dir / "modem-config"
apk_path = args.oem_dir / args.overlay
apk = APK(apk_path.as_posix())
print(f"Opened {apk}")
file = apk.get_file("res/xml/service_providers.xml")

from xml.etree import ElementTree as ET

"""
NOTE:
Use get_xml_obj to get an immutable representation of the tree
Use get_xml to get a formatted string representation
Use get_buff to get a oneline representation without formatting tokens

ET keeps the formatting internally, and spits this out in `.write()`.
Inserted elements are not formatted properly.
"""
et = ET.fromstring(AXMLPrinter(file).get_xml())
assert et.tag == "service_provider_sim_configs"

mbn_paths = set()
config_ids = set()

for el in et:
    assert el.tag == "service_provider_sim_config"

    config_id = el.get("sim_config_id")
    config_ids.add(config_id)
    mcc, mnc = el.find("mcc"), el.find("mnc")
    if mcc is not None:
        mcc = mcc.text
    if mnc is not None:
        mnc = mnc.text

    # Useful to find unused attrs/elements.
    # TODO: Accumulate over all config entries and print set only once
    """
    unused = set(el.keys())
    unused = unused.remove('sim_config_id')
    if unused:
        print(f"Unused attributes: {unused}")
    unused = set(child.tag for child in el)
    unused = unused - {'mcc', 'mnc'}
    if unused:
        print(f"Unused elements: {unused}")
    """

    conf_path = oem_modem_config_dir / config_id / "modem.conf"
    if not conf_path.exists():
        print(f"WARNING: No config file found for {config_id} ({mcc}, {mnc})")
        continue
    fw_file = conf_path.read_text()
    print(f"{config_id} ({mcc}, {mnc}) => {fw_file}")
    mbn_paths.add(fw_file)
    if args.mcfg_dir:
        local_mbn = args.mcfg_dir / fw_file
        if not local_mbn.exists():
            print(f"WARNING: {local_mbn} does not exist!")

    # TODO: Also omit the above printing, or add a separate variable.
    # Looping through the xml becomes unnecessary at this point.
    if args.add_paths:
        p = ET.SubElement(el, "path")
        p.text = fw_file

# Accumulate and print information about unreferenced files available in the tree:

available_configs = {
    conf.parent.name for conf in oem_modem_config_dir.rglob("modem.conf")
}

unreferenced = available_configs - config_ids
if unreferenced:
    print(f"Unreferenced config files: {unreferenced}")

if args.mcfg_dir:
    available_mbns = {
        str(conf.relative_to(args.mcfg_dir))
        for conf in args.mcfg_dir.rglob("mcfg_sw.mbn")
    }
    unreferenced = available_mbns - mbn_paths
    if unreferenced:
        print(
            f"{len(unreferenced)} out of {len(available_mbns)} .mbn files unreferenced:"
        )
        print("\n".join(unreferenced))

# Finally, write the extracted (and eventually modified) config to a file:
args.output.parent.mkdir(parents=True, exist_ok=True)
ET.ElementTree(et).write(args.output.as_posix())
# from lxml import etree
# Path('config.xml').write_text(etree.tostring(ET.ElementTree(et)), 'utf8')
