#!/usr/bin/env python3
#
# Copyright (C) 2017, ARM Limited, All Rights Reserved
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This file is part of mbed TLS (https://tls.mbed.org)

# Limit logs and print some useful stats to stderr

import sys
import select
import datetime
import re

progress = 0

TICK_SIZE = datetime.timedelta( hours=1 )

def process_tick( ):
    global progress
    if progress == 0:
        print( "No progress for 1hr... quiting." )
        sys.exit( 1 )

    print( "Progress! " + str( progress ) + "/hr" )
    progress = 0

def maybe_process_tick( ):
    global last_tick
    now = datetime.datetime.now()
    if last_tick + TICK_SIZE < now:
        process_tick( )
        last_tick = now
    if started + datetime.timedelta( days=1 ) < now:
        print( "Fuzzer ran for a day, time to refresh" )
        sys.exit( 1 )

def process_line( line ):
    global progress
    if line == '':
        sys.exit( 0 )

    if re_progress.match( line ):
        progress += 1

    if silence.match( line ):
        return

    print( line, end="" )
    sys.stdout.flush( )

timeout = 60

def process_stdin( ):
    while True:
        rlist, _, _ = select.select( [sys.stdin], [], [], timeout )
        if rlist:
            line = sys.stdin.readline( )
            process_line( line )
        maybe_process_tick( )

re_progress = re.compile( ".*(NEW|REDUCE|RELOAD).*" )
silence = re.compile( ".*(REDUCE|RELOAD).*" )

started = datetime.datetime.now()
last_tick = started

process_stdin( )

sys.exit( exit_code )

