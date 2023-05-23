# frozen_string_literal: true

def execute_command(action, scenario_name = '', scenario_metadata = '')

  address = if Maze.config.farm == :bb
              if Maze.config.aws_public_ip
                Maze.public_address
              else
                'local:9339'
              end
            else
              'bs-local.com:9339'
            end

  command = {
    action: action,
    scenario_name: scenario_name,
    scenario_metadata: scenario_metadata,
    endpoint: "http://#{address}/traces",
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

When('I run {string} configured as {string}') do |scenario_name, scenario_metadata|
  execute_command 'run_scenario', scenario_name, scenario_metadata
end

When('I load scenario {string}') do |scenario_name|
  execute_command 'load_scenario', scenario_name
end

When('I invoke {string}') do |function|
  execute_command 'invoke', function
end

When("I relaunch the app after shutdown") do
  max_attempts = 20
  attempts = 0
  state = Maze.driver.app_state('com.bugsnag.mazeracer')
  until (attempts >= max_attempts) || state == :not_running
    attempts += 1
    state = Maze.driver.app_state('com.bugsnag.mazeracer')
    sleep 0.5
  end
  $logger.warn "App state #{state} instead of #{expected_state} after 10s" unless state == :not_running

  Maze.driver.launch_app
end

Then("I run {string} and discard the initial p-value request") do |scenario|
  steps %Q{
    When I run "#{scenario}"
    And I receive and discard the initial p-value request
  }
end

Then("I run {string} configured as {string} and discard the initial p-value request") do |scenario, configured|
  steps %Q{
    When I run "#{scenario}" configured as "#{configured}"
    And I receive and discard the initial p-value request
  }
end

Then('the {word} payload field {string} attribute {string} matches the regex {string}') do |request_type, field, key, regex_string|
  regex = Regexp.new(regex_string)
  list = Maze::Server.list_for(request_type)
  attributes = Maze::Helper.read_key_path(list.current[:body], "#{field}.attributes")
  attribute = attributes.find { |a| a['key'] == key }
  value = attribute["value"]["intValue"]
  Maze.check.match(regex, value)
end
