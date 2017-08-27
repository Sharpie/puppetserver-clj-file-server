env_data = Puppet.lookup(:environments).list.map do |env|
  [
    env.name.to_s,
    {
      "modulepath" => env.full_modulepath
    }
  ]
end

Hash[env_data]
