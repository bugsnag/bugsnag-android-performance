# frozen_string_literal: true

def execute_command(action, scenario_name = '', scenario_metadata = '')
  command = {
    action: action,
    scenario_name: scenario_name,
    scenario_metadata: scenario_metadata,
    endpoint: 'http://bs-local.com:9339/traces',
  }
  Maze::Server.commands.add command

  # Ensure fixture has read the command
  count = 600
  sleep 0.1 until Maze::Server.commands.remaining.empty? || (count -= 1) < 1
  raise 'Test fixture did not GET /command' unless Maze::Server.commands.remaining.empty?
end

When('I run {string}') do |scenario_name|
  execute_command 'run_scenario', scenario_name
end

Then('the {word} payload field {string} attribute {string} equals {string}') do |request_type, field, key, expected|
  list = Maze::Server.list_for(request_type)
  attributes = Maze::Helper.read_key_path(list.current[:body], "#{field}.attributes")
  Maze.check.equal attributes.find { |a| a['key'] == key }, { 'key' => key, 'value' => { 'stringValue' => expected } }
end