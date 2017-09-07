# Implemented in Ruby because Java doesn't touch things like the
# euid and egid system calls with a 10 foot pole. JRuby has got
# it figured out though.
{
  'owner' => Process.euid,
  'group' => Process.egid,
  'mode' => 0644
}
