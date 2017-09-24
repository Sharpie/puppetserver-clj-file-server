# Slots a build of the clojure file server into a PE install. Use this class
# by adding it to the "PE Masters" group.
class clj_file_server (
  Enum['present', 'absent'] $ensure = 'present',
) {

  # Add a services.d directory to the bootstrap path that can be used to
  # slot in additional services.
  Pe_ini_setting <| title == "puppetserver initconf bootstrap_config" |> {
    value => '/etc/puppetlabs/puppetserver/bootstrap.cfg,/etc/puppetlabs/puppetserver/services.d/'
  }

  file {'/etc/puppetlabs/puppetserver/services.d':
    ensure => directory,
    owner  => 'pe-puppet',
    group  => 'pe-puppet',
    mode   => '0755',
  }

  file {'/etc/puppetlabs/puppetserver/services.d/file-serving.cfg':
    ensure => $ensure ? {
      'present' => file,
      'absent'  => absent,
    },
    owner   => 'pe-puppet',
    group   => 'pe-puppet',
    mode    => '0644',
    content => "puppetlabs.services.file-serving.file-serving-service/clj-file-serving-service\n",
    require => Pe_ini_setting['puppetserver initconf bootstrap_config'],
    notify  => Exec['pe-puppetserver service full restart'],
  }

  if versioncmp($::facts['pe_server_version'], '2017.3.0') >= 0 {
    # Use the new JAR directory added to Puppet Server 5.1.0 that
    # is on the classpath by default (SERVER-249).
    file {'/opt/puppetlabs/server/data/puppetserver/jars/clj-file-server.jar':
      ensure => file,
      owner  => 'root',
      group  => 'root',
      mode   => '0644',
      source => 'puppet:///modules/clj_file_server/clj-file-server.jar',
      notify  => Exec['pe-puppetserver service full restart'],
    }
  } else {
    # For older versions, patch the JAR into the classpath.
    file {'/opt/puppetlabs/server/apps/puppetserver/clj-file-server.jar':
      ensure => file,
      owner  => 'root',
      group  => 'root',
      mode   => '0644',
      source => 'puppet:///modules/clj_file_server/clj-file-server.jar',
      notify  => Exec['pe-puppetserver service full restart'],
    }

    file_line {'add file server JAR to classpath':
      ensure => present,
      path  => '/opt/puppetlabs/server/apps/puppetserver/cli/apps/start',
      match => '^\s*-cp',
      line  => '  -cp ${INSTALL_DIR}/puppet-server-release.jar:${INSTALL_DIR}/clj-file-server.jar \\',
      replace => true,
      notify  => Exec['pe-puppetserver service full restart'],
    }
  }
}
