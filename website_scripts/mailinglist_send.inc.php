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
