/*
 *  Copyright (c) 2017-2021, Arm Limited, All Rights Reserved
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

<?php
# Global configuration
#
$includes='../web_framework/includes/';
$site_includes='../includes/';

error_reporting(E_ALL | E_STRICT);
ini_set("display_errors", 1);

require_once($includes.'wf_core.inc.php');

require_once($includes."config_values.inc.php");
require_once($site_includes."mailinglist_logic.inc.php");

$list = new Mailinglist($global_info, 'polarssl');

while(TRUE)
{
    $queue_entry = $list->get_queue_entry();
    if ($queue_entry === FALSE)
        break;

    $queue_entry->update_field('status', 'sending');
    $campaign = $queue_entry->get_campaign();
    $entry = $queue_entry->get_entry();

    printf("Sending '".$campaign->title."' to '".$entry->email."'\n");
    $mail = $list->get_personalized_message($campaign, $entry);
    $mail->send();
    $queue_entry->update_field('status', 'sent');
    $queue_entry->update_field('sent', date('Y-m-d H:i:s'));
}

$list->update_campaigns();
?>
