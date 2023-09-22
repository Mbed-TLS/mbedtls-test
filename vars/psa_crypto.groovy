/*
 *  Copyright (c) 2018-2023, Arm Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
 */

import hudson.model.Result
import jenkins.model.CauseOfInterruption
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void run_pr_job() {
    if (env.TARGET_BRANCH != 'main') {
        echo 'PR target is not "main" branch - not building.'
        throw new FlowInterruptedException(Result.NOT_BUILT, new CauseOfInterruption[0])
    }
    mbedtls.run_pr_job(true, 'psa-crypto')
}
