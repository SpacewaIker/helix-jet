import sys
import tomllib
import argparse

def flatten(prefix: str, data: dict, out: dict):
    for key, value in data.items():
        full_key = f"{prefix}.{key}"
        if isinstance(value, dict):
            flatten(full_key, value, out)
        elif isinstance(value, list):
            for i, item in enumerate(value):
                if isinstance(item, dict):
                    flatten(f"{full_key}.{i}", item, out)
                else:
                    out[f"{full_key}.{i}"] = item
        else:
            out[full_key] = value

parser = argparse.ArgumentParser()
parser.add_argument("input", help="Input TOML file")
parser.add_argument("output", help="Output properties file")
parser.add_argument("--languages", nargs="*", help="Languages to include (default: all)")
args = parser.parse_args()

with open(args.input, "rb") as f:
    data = tomllib.load(f)

languages = set(args.languages) if args.languages else None

with open(args.output, "w") as f:
    for grammar in data["grammar"]:
        name = grammar["name"]
        if languages and name not in languages:
            continue
        props = {}
        flatten(f"grammar.{name}", {k: v for k, v in grammar.items() if k != "name"}, props)
        for key, value in props.items():
            f.write(f"{key}={value}\n")