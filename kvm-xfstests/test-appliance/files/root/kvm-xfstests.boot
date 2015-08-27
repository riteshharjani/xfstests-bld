#!/bin/bash -e
#
# This script is executed at the end of each multiuser runlevel
# to kick off the test appliance commands

parse() {
if grep -q " $1=" /proc/cmdline; then
   cat /proc/cmdline | sed -e "s/.* $1=//" | sed -e 's/ .*//'
else
   echo ""
fi
}

. /root/test-config

if test -e /usr/local/lib/gce-parse
then
    . /usr/local/lib/gce-funcs
    /usr/local/lib/gce-parse
fi

FSTESTCFG=$(parse fstestcfg | sed -e 's/,/ /g')
FSTESTSET=$(parse fstestset | sed -e 's/,/ /g')
FSTESTOPT=$(parse fstestopt | sed -e 's/,/ /g')
FSTESTTYP=$(parse fstesttyp)
FSTESTAPI=$(parse fstestapi | sed -e 's/\./ /g')
timezone=$(parse fstesttz)
MNTOPTS=$(parse mount_opts)
CMD=$(parse cmd)
FSTESTEXC=$(parse fstestexc | sed -e 's/\./ /g')

export FSTESTCFG
export FSTESTSET
export FSTESTOPT
export FSTESTTYP
export FSTESTAPI
export FSTESTEXC
export MNTOPTS

if test -n "$timezone" -a -f /usr/share/zoneinfo/$timezone
then
    ln -sf /usr/share/zoneinfo/$timezone /etc/localtime
    echo $timezone > /etc/timezone
fi

if test "$CMD" = "ver"
then
	/usr/local/sbin/ver
	poweroff -f > /dev/null 2>&1
fi

REGEXP="( Linux version )|(^FSTEST)|(^MNTOPTS)|(^CPUS:)|(^MEM:)|(^BEGIN)|(^MOUNT_OPTIONS)|(^MKFS_OPTIONS)|(^END)|(^EXT4-fs error)|(WARNING)|(^Ran: )|(^Failures: )|(^Passed)|(inconsistent)"
REGEXP_FAILURE="(^FSTEST)|(^MNTOPTS)|(^CPUS:)|(^MEM:)|(^BEGIN)|(^Failures: )|(^Passed)"

if test -n "$FSTESTCFG" -a -n "$FSTESTSET"
then
    if test -n "$RUN_ON_GCE"
    then
	/usr/local/lib/gce-setup
	/root/runtests.sh >& /results/runtests.log
	egrep "$REGEXP" < /results/runtests.log > /results/summary
	egrep "$REGEXP_FAILURE" < /results/runtests.log > /results/failures
	REPORT_EMAIL=$(gce_attribute report_email)
	if test -n "$REPORT_EMAIL"
	then
	    mail -s "xfstests results $DATECODE - $(uname -r)" \
		 "$REPORT_EMAIL" < /results/failures
	fi
	tar -C /results -cf - . | xz -6e > /tmp/results.tar.xz
	gsutil cp /tmp/results.tar.xz \
	       gs://$GS_BUCKET/results.$DATECODE.$(uname -r).tar.xz
	gce-shutdown
    else
	/root/runtests.sh
	poweroff -f > /dev/null 2>&1
    fi
fi
