-- Accumulates txBytes and rxBytes per package group, includes session data
function onConnectivityDataWithSession(published_data, state)
    log("entering onConnectivityDataWithSession");
    res = {}
    -- Save session data which unlike normal fields of published_data is not array type and exists
    -- for every session
    -- sessionId: integer that starts from 1 and increases for each new session to uniquely
    -- identify each session. It's reset to 1 upon reboot.
    res['sessionId'] = published_data['sessionId']
    -- sessionState: either 1 (STATE_EXIT_DRIVING_SESSION) meaning currently outside a session or
    -- 2 (STATE_ENTER_DRIVING_SESSION) meaning currently in a session (device is on). For
    -- connectivity this is always 1 because data is calculated at session end.
    res['sessionState'] = published_data['sessionState']
    -- createdAtSinceBootMillis: milliseconds since boot
    res['createdAtSinceBootMillis'] = published_data['createdAtSinceBootMillis']
    -- createdAtMillis: current time in milliseconds unix time
    res['createdAtMillis'] = published_data['createdAtMillis']
    -- bootReason: last boot reason
    res['bootReason'] = published_data['bootReason']

    -- If we don't have metrics data then exit with only sessions data right now
    if published_data['packages'] == nil then
        -- on_metrics_report(r) sends r as finished result table
        -- on_metrics_report(r, s) sends r as finished result table while also sending
        -- s as intermediate result that will be sent next time as 'state' param
        on_metrics_report(res)
    end

    -- Accumulate rxBytes and txBytes for each package group, save uid
    rx = {}
    tx = {}
    uids = {}
    -- Go through the arrays (all same length as packages array) and accumulate rx and tx for each
    -- package name group. In the packages array an entry can be a conglomeration of multiple package
    -- names (eg. ["com.example.abc", "com.example.cdf,com.vending.xyz"] the 2nd entry has 2
    -- package names because it's not distinguishable which made the data transfers)
    for i, ps in ipairs(published_data['packages']) do
        if rx[ps] == nil then
            rx[ps] = 0
            tx[ps] = 0
        end
        -- For each package group accumulate the rx and tx separately, record uid
        rx[ps] = rx[ps] + published_data['rxBytes'][i]
        tx[ps] = tx[ps] + published_data['txBytes'][i]
        uids[ps] = published_data['uid'][i]
    end
    -- For each package group name combine rx, tx and uid into one string for print
    for p, v in pairs(rx) do
        res[p] = 'rx: ' .. rx[p] .. ', tx: ' .. tx[p] .. ', uid: ' .. uids[p]
    end
    on_metrics_report(res)
end
