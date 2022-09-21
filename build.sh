#! /usr/bin/env bash

set -euo pipefail

mvn compile assembly:single -U
